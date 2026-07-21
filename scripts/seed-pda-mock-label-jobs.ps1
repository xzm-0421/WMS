# Create label print jobs matching PDA mock materials
$BaseUrl = if ($env:WMS_API) { $env:WMS_API } else { "http://localhost:9980/api/v1" }
$jsonPath = Join-Path $PSScriptRoot "pda-mock-label-jobs.json"
$lines = Get-Content -Path $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json

$created = 0
$skipped = 0

foreach ($line in $lines) {
    $bodyObj = [ordered]@{
        sourceBillNo = $line.sourceBillNo
        materialCode = $line.materialCode
        materialName = $line.materialName
        specification = $line.specification
        batchNo = $line.batchNo
        unitCode = $line.unitCode
        quantity = $line.quantity
        productionDate = $line.productionDate
        barcodeContent = "$($line.materialCode)|$($line.batchNo)|$($line.quantity)"
        barcodeType = "QR"
        labelWidthMm = 60
        labelHeightMm = 40
        copies = 1
    }
    $body = $bodyObj | ConvertTo-Json -Compress

    try {
        $res = Invoke-RestMethod -Method POST -Uri "$BaseUrl/integration/kingdee/label-print" -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
        if ($res.code -eq 200) {
            Write-Host "[OK] $($line.materialCode) -> $($res.data.jobId)"
            $created++
        } else {
            Write-Host "[SKIP] $($line.materialCode): $($res.message)"
            $skipped++
        }
    } catch {
        Write-Host "[ERR] $($line.materialCode): $($_.Exception.Message)"
        if ($_.ErrorDetails.Message) { Write-Host "  $($_.ErrorDetails.Message)" }
    }
}

Write-Host ""
Write-Host "Done: created=$created skipped=$skipped"
