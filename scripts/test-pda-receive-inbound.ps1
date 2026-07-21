# PDA receive inbound test script
$BaseUrl = if ($env:WMS_API) { $env:WMS_API } else { "http://localhost:9980/api/v1" }
$BillNo = if ($env:BILL_NO) { $env:BILL_NO } else { "CGSL260601127" }
$PreferredWarehouse = if ($env:WAREHOUSE_CODE) { $env:WAREHOUSE_CODE } else { "CK031" }
$MaterialCode = if ($env:MATERIAL_CODE) { $env:MATERIAL_CODE } else { "S006-A315015D-0000-A01" }
$SubmitQty = if ($env:SUBMIT_QTY) { [decimal]$env:SUBMIT_QTY } else { 5 }

function Invoke-WmsApi {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null
    )
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $uri = "$BaseUrl$Path"
    if ($Body -ne $null) {
        $json = $Body | ConvertTo-Json -Depth 6 -Compress
        $resp = Invoke-WebRequest -Method $Method -Uri $uri -Headers $headers -Body $json -UseBasicParsing
    } else {
        $resp = Invoke-WebRequest -Method $Method -Uri $uri -Headers $headers -UseBasicParsing
    }
    $obj = $resp.Content | ConvertFrom-Json
    if ($obj.code -and $obj.code -ne 200) {
        throw "API error [$($obj.code)] $($obj.message) $Path"
    }
    return $obj
}

function Ensure-TestLocation {
    param([string]$Token, [string]$WarehouseCode)
    $locCode = "$WarehouseCode-TEST01"
    try {
        $existing = Invoke-WmsApi -Method GET -Path "/mobile/locations?warehouseCode=$WarehouseCode" -Token $Token
        if ($existing.data.Count -gt 0) { return $WarehouseCode }
    } catch {
        # ignore
    }

    Write-Host "Creating test location for warehouse $WarehouseCode ..."
    try {
        Invoke-WmsApi -Method POST -Path "/base/locations/zones" -Token $Token -Body @{
            warehouseCode = $WarehouseCode
            zoneCode = "TEST"
            zoneName = "Test Zone"
        } | Out-Null
    } catch {
        $msg = $_.Exception.Message
        if ($msg -notlike '*ZONE_CODE_EXISTS*') { throw }
    }
    Invoke-WmsApi -Method POST -Path "/base/locations" -Token $Token -Body @{
        locationCode = $locCode
        locationName = "Test Location 01"
        warehouseCode = $WarehouseCode
        zoneCode = "TEST"
        locationType = "STORAGE"
        status = 1
    } | Out-Null
    Write-Host "Created location $locCode"
    return $WarehouseCode
}

$PwdHash123456 = "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92"

Write-Host "==> Login PDA ..."
$login = Invoke-WmsApi -Method POST -Path "/auth/mobile/login" -Body @{
    username = "admin"
    password = $PwdHash123456
    deviceNo = "test-pda"
}
$token = $login.data.accessToken
if (-not $token) { throw "Login failed" }
Write-Host "Login OK"

Write-Host ""
Write-Host "==> Load receive notice $BillNo ..."
$detail = Invoke-WmsApi -Method GET -Path "/mobile/receive-notice/$BillNo" -Token $token
Write-Host "Lines: $($detail.data.lines.Count)"
Write-Host "Supplier: $($detail.data.supplierCode) / $($detail.data.supplierName)"
Write-Host "Warehouse session=$($detail.data.warehouseCode) erp=$($detail.data.erpWarehouseCode)"

$WarehouseCode = $detail.data.erpWarehouseCode
if (-not $WarehouseCode) { $WarehouseCode = $PreferredWarehouse }
try {
    Invoke-WmsApi -Method POST -Path "/integration/kingdee/master-data/sync/warehouses?keyword=$WarehouseCode" -Token $token | Out-Null
} catch {
    # ignore sync errors
}
$WarehouseCode = Ensure-TestLocation -Token $token -WarehouseCode $WarehouseCode

Write-Host ""
Write-Host "==> Scan material $MaterialCode ..."
$scan = Invoke-WmsApi -Method POST -Path "/mobile/receive-notice/$BillNo/scan" -Token $token -Body @{
    barcodeContent = $MaterialCode
    deviceNo = "test-pda"
}
$lineNo = $scan.data.lineNo
Write-Host "Line $lineNo pending: $($scan.data.pendingSubmitQty)"

Write-Host "==> Set qty $SubmitQty ..."
$qty = Invoke-WmsApi -Method PUT -Path "/mobile/receive-notice/$BillNo/lines/$lineNo/qty" -Token $token -Body @{
    qty = $SubmitQty
}
Write-Host "Pending: $($qty.data.pendingSubmitQty)"

Write-Host ""
Write-Host "==> Submit inbound warehouse=$WarehouseCode ..."
$submit = Invoke-WmsApi -Method POST -Path "/mobile/receive-notice/$BillNo/submit" -Token $token -Body @{
    warehouseCode = $WarehouseCode
    supplierCode = $detail.data.supplierCode
    supplierName = $detail.data.supplierName
    erpWarehouseCode = $detail.data.erpWarehouseCode
    autoAllocateLocation = $true
    deviceNo = "test-pda"
    remark = "API test submit"
}
Write-Host "Message: $($submit.data.message)"
Write-Host "BatchNo: $($submit.data.batchNo)"
Write-Host "ErpBillNo: $($submit.data.erpBillNo)"
Write-Host "ErpSyncMessage: $($submit.data.erpSyncMessage)"
Write-Host "Lines: $($submit.data.lineCount) TotalQty: $($submit.data.totalQty) ErpSync: $($submit.data.erpSyncStatus)"
if ($submit.data.recordNos) {
    foreach ($recordNo in $submit.data.recordNos) {
        $rec = Invoke-WmsApi -Method GET -Path "/inbound/pda-records/$recordNo" -Token $token
        Write-Host "  Record $recordNo $($rec.data.materialCode) qty=$($rec.data.quantity) loc=$($rec.data.locationCode) batch=$($rec.data.batchNo)"
    }
}

Write-Host ""
Write-Host "==> Done"
