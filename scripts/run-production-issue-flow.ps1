/**
 * 生产领料单联调脚本：登录 → 解析单号 → 明细 → 扫物料 → 提交出库
 * 用法：在项目根目录执行
 *   powershell -File scripts/run-production-issue-flow.ps1
 */
$ErrorActionPreference = 'Stop'
$base = if ($env:WMS_API_BASE) { $env:WMS_API_BASE } else { 'http://localhost:9980/api/v1' }
$user = if ($env:WMS_USER) { $env:WMS_USER } else { 'admin' }
# PDA 登录密码按前端约定：admin+123456 使用固定 sha256
$pwdHash = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92'
$billNo = 'YLD20260715001'
$deviceNo = 'PDA-SN-DEV001'

function Invoke-Api($method, $path, $body = $null, $token = $null) {
  $headers = @{ 'Content-Type' = 'application/json' }
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  $headers['X-Device-ID'] = $deviceNo
  $params = @{
    Uri = "$base$path"
    Method = $method
    Headers = $headers
    UseBasicParsing = $true
  }
  if ($null -ne $body) {
    $params.Body = ($body | ConvertTo-Json -Compress -Depth 6)
  }
  $resp = Invoke-RestMethod @params
  if ($resp.code -ne 200 -and $resp.code -ne 0) {
    throw "API $path failed: code=$($resp.code) msg=$($resp.message)"
  }
  return $resp
}

Write-Host "== 1. Login =="
$login = Invoke-Api POST '/auth/mobile/login' @{
  username = $user
  password = $pwdHash
  deviceNo = $deviceNo
}
$token = $login.data.accessToken
if (-not $token) { throw 'login missing accessToken' }
Write-Host "OK token acquired"

Write-Host "== 2. Resolve barcode =="
$resolved = Invoke-Api POST "/mobile/notice-bill/PRODUCTION_ISSUE/resolve-barcode" @{
  barcodeContent = $billNo
} $token
Write-Host "billNo=$($resolved.data.billNo)"

Write-Host "== 3. Load detail =="
$detail = Invoke-Api GET "/mobile/notice-bill/PRODUCTION_ISSUE/$billNo" $null $token
$lines = $detail.data.lines
Write-Host "lines=$($lines.Count) warehouse=$($detail.data.warehouseCode)"
$lines | ForEach-Object { Write-Host ("  L{0} {1} plan={2}" -f $_.lineNo, $_.materialCode, $_.planQty) }

Write-Host "== 4. Scan material PITEST-001 =="
$scan1 = Invoke-Api POST "/mobile/notice-bill/PRODUCTION_ISSUE/$billNo/scan" @{
  barcodeContent = 'PITEST-001B20260715'
  deviceNo = $deviceNo
} $token
Write-Host ("scanned {0} pending={1}" -f $scan1.data.materialCode, $scan1.data.pendingSubmitQty)

Write-Host "== 5. Scan material PITEST-002 =="
$scan2 = Invoke-Api POST "/mobile/notice-bill/PRODUCTION_ISSUE/$billNo/scan" @{
  barcodeContent = 'PITEST-002B20260715'
  deviceNo = $deviceNo
} $token
Write-Host ("scanned {0} pending={1}" -f $scan2.data.materialCode, $scan2.data.pendingSubmitQty)

Write-Host "== 6. Update qty line1 = 10 =="
$q1 = Invoke-Api PUT "/mobile/notice-bill/PRODUCTION_ISSUE/$billNo/lines/1/qty" @{ qty = 10 } $token
Write-Host ("line1 pending={0}" -f $q1.data.pendingSubmitQty)

Write-Host "== 7. Update qty line2 = 5 =="
$q2 = Invoke-Api PUT "/mobile/notice-bill/PRODUCTION_ISSUE/$billNo/lines/2/qty" @{ qty = 5 } $token
Write-Host ("line2 pending={0}" -f $q2.data.pendingSubmitQty)

Write-Host "== 8. Submit outbound =="
$submit = Invoke-Api POST "/mobile/notice-bill/PRODUCTION_ISSUE/$billNo/submit" @{
  deviceNo = $deviceNo
  supplierCode = 'WS01'
  supplierName = '一车间'
} $token
Write-Host ("OK batchNo={0} lineCount={1} totalQty={2} message={3}" -f `
  $submit.data.batchNo, $submit.data.lineCount, $submit.data.totalQty, $submit.data.message)

Write-Host ""
Write-Host "生产领料单联调流程完成。"
Write-Host "PDA 手动验证："
Write-Host "  出库业务 → 生产领料 → 扫/搜索 YLD20260715001"
Write-Host "  物料扫码：PITEST-001B20260715 或 PITEST-002B20260715"
Write-Host "  调整数量后点「提交出库」"
