# 金蝶 OpenAPI（kapi OAuth2）联调验证脚本
# 用法（PowerShell 7+）：
#   pwsh -File .\verify-kingdee-token.ps1
#   pwsh -File .\verify-kingdee-token.ps1 -BaseUrl http://192.168.0.176:9980 -Username admin -Password 123456
#
# 说明：
# - 后端所有接口前缀为 /api/v1，默认端口 9980。
# - 采用移动端登录 /api/v1/auth/mobile/login（免图片验证码）；密码按客户端约定传 SHA-256(明文) 十六进制。
# - 仅调用只读接口：token/verify（校验令牌）与 probe（探测字段键，不写库）。

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:9980',
    [string]$Username = 'admin',
    [string]$Password = '123456',
    [string]$FormId = 'BD_MATERIAL',
    [string]$FieldKeys = 'FNumber,FMATERIALID'
)

$ErrorActionPreference = 'Stop'
$api = ($BaseUrl.TrimEnd('/')) + '/api/v1'

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [hashtable]$Headers,
        [object]$Body
    )
    $params = @{ Method = $Method; Uri = $Uri }
    if ($Headers) { $params.Headers = $Headers }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    try {
        return Invoke-RestMethod @params
    } catch {
        $detail = $_.ErrorDetails.Message
        if (-not $detail) { $detail = $_.Exception.Message }
        throw "请求失败 $Method $Uri`n$detail"
    }
}

function Get-Sha256Hex {
    param([string]$Text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        return ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join ''
    } finally {
        $sha.Dispose()
    }
}

Write-Host "== 金蝶 OpenAPI 联调验证 ==" -ForegroundColor Cyan
Write-Host "BaseUrl: $BaseUrl"

# 1) 登录获取 JWT
$passwordHash = Get-Sha256Hex $Password
Write-Host "[1/3] 登录 $api/auth/mobile/login ..."
$login = Invoke-Api -Method POST -Uri "$api/auth/mobile/login" -Body @{
    username = $Username
    password = $passwordHash
    deviceNo = 'verify-script'
}
if ($login.code -ne 200) { throw "登录失败: $($login.message)" }
$token = $login.data.accessToken
if (-not $token) { throw '登录成功但未返回 accessToken' }
$headers = @{ Authorization = "Bearer $token" }
Write-Host "      登录成功（$Username）" -ForegroundColor Green

# 2) 校验金蝶 access_token
Write-Host "[2/3] 校验金蝶令牌 $api/integration/kingdee/master-data/token/verify ..."
$verify = Invoke-Api -Method GET -Uri "$api/integration/kingdee/master-data/token/verify" -Headers $headers
if ($verify.code -ne 200) { throw "令牌校验失败: $($verify.message)" }
Write-Host "      返回:" -ForegroundColor Green
$verify.data | ConvertTo-Json -Depth 8 | Write-Host

# 3) 只读探测字段键（验证内码字段是否可查）
$encodedFieldKeys = [uri]::EscapeDataString($FieldKeys)
$probeUri = "$api/integration/kingdee/master-data/probe?formId=$FormId&fieldKeys=$encodedFieldKeys"
Write-Host "[3/3] 探测 $FormId ($FieldKeys) ..."
$probe = Invoke-Api -Method POST -Uri $probeUri -Headers $headers
if ($probe.code -ne 200) { throw "探测失败: $($probe.message)" }
Write-Host "      返回（前 1 行）:" -ForegroundColor Green
$probe.data | ConvertTo-Json -Depth 8 | Write-Host

Write-Host "完成。" -ForegroundColor Cyan
