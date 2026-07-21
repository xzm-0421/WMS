<#
.SYNOPSIS
  一键创建 WMS SQL Server 数据库。

.DESCRIPTION
  账号密码等配置请编辑同目录下 init-wms-db.config.json。
  命令行参数可覆盖配置文件中的同名字段。

.EXAMPLE
  .\scripts\init-wms-db.ps1

.EXAMPLE
  .\scripts\init-wms-db.ps1 -UseDocker

.EXAMPLE
  .\scripts\init-wms-db.ps1 -AppPassword 'NewPass@123'
#>
[CmdletBinding()]
param(
    [string]$SqlHost,
    [int]$SqlPort = 0,
    [string]$SaUser,
    [string]$SaPassword,
    [string]$Database,
    [string]$AppUser,
    [string]$AppPassword,
    [string]$Collation,
    [switch]$UseDocker,
    [switch]$SkipWait,
    [string]$ConfigPath
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not $ConfigPath) {
    $ConfigPath = Join-Path $PSScriptRoot "init-wms-db.config.json"
}
$SqlTemplate = Join-Path $PSScriptRoot "sql\init-wms-db.sql"

if (-not (Test-Path $ConfigPath)) {
    throw "找不到配置文件: $ConfigPath"
}
if (-not (Test-Path $SqlTemplate)) {
    throw "找不到 SQL 模板: $SqlTemplate"
}

$config = Get-Content -Path $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json

function Coalesce([string]$preferred, [string]$fallback) {
    if ($null -ne $preferred -and $preferred -ne "") { return $preferred }
    return $fallback
}

$SqlHost = Coalesce $SqlHost (Coalesce $env:SQLSERVER_HOST $config.sqlHost)
if ($SqlPort -le 0) {
    if ($env:SQLSERVER_PORT) { $SqlPort = [int]$env:SQLSERVER_PORT }
    else { $SqlPort = [int]$config.sqlPort }
}
$SaUser = Coalesce $SaUser $config.saUser
$SaPassword = Coalesce $SaPassword (Coalesce $env:SQLSERVER_SA_PASSWORD $config.saPassword)
$Database = Coalesce $Database (Coalesce $env:SQLSERVER_DB $config.database)
$AppUser = Coalesce $AppUser (Coalesce $env:SQLSERVER_USER $config.appUser)
$AppPassword = Coalesce $AppPassword (Coalesce $env:SQLSERVER_PASSWORD $config.appPassword)
$Collation = Coalesce $Collation $config.collation
if (-not $Collation) { $Collation = "Chinese_PRC_CI_AS" }
if (-not $UseDocker -and $config.useDocker) { $UseDocker = $true }

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "[!] $msg" -ForegroundColor Yellow }

function Find-SqlCmd {
    $cmd = Get-Command sqlcmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "${env:ProgramFiles}\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn\SQLCMD.EXE",
        "${env:ProgramFiles}\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE",
        "${env:ProgramFiles(x86)}\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn\SQLCMD.EXE"
    )
    foreach ($p in $candidates) {
        if (Test-Path $p) { return $p }
    }
    return $null
}

function Wait-SqlReady {
    param([string]$SqlCmdPath, [string]$Server, [string]$User, [string]$Password, [int]$Seconds = 90)
    Write-Step "等待 SQL Server 就绪 ($Server) ..."
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            & $SqlCmdPath -S $Server -U $User -P $Password -C -Q "SELECT 1" -b -o $null 2>$null
            if ($LASTEXITCODE -eq 0) {
                Write-Ok "SQL Server 已就绪"
                return
            }
        } catch { }
        Start-Sleep -Seconds 3
        Write-Host "." -NoNewline
    }
    throw "等待 SQL Server 超时（${Seconds}s），请确认服务已启动且账号密码正确。"
}

function Ensure-DockerSql {
    Write-Step "启动 Docker SQL Server（wms-deploy）..."
    $composeDir = Join-Path $Root "wms-deploy"
    if (-not (Test-Path (Join-Path $composeDir "docker-compose.yml"))) {
        throw "找不到 wms-deploy/docker-compose.yml"
    }
    Push-Location $composeDir
    try {
        docker compose up -d sqlserver
        if ($LASTEXITCODE -ne 0) { throw "docker compose up sqlserver 失败" }
    } finally {
        Pop-Location
    }
    Write-Ok "容器 sqlserver 已启动"
}

function New-RenderedSql {
    $tpl = Get-Content -Path $SqlTemplate -Raw -Encoding UTF8
    $sql = $tpl.
        Replace("{{DATABASE}}", $Database).
        Replace("{{APP_USER}}", $AppUser).
        Replace("{{APP_PASSWORD}}", $AppPassword.Replace("'", "''")).
        Replace("{{COLLATION}}", $Collation)
    $tmp = Join-Path $env:TEMP ("wms-init-db-" + [guid]::NewGuid().ToString("N") + ".sql")
    Set-Content -Path $tmp -Value $sql -Encoding UTF8
    return $tmp
}

Write-Host "WMS 一键建库" -ForegroundColor White
Write-Host "配置文件: $ConfigPath" -ForegroundColor DarkGray
Write-Host "目标：database=$Database  login=$AppUser" -ForegroundColor DarkGray
Write-Host "管理账号：$SaUser @ ${SqlHost}:${SqlPort}" -ForegroundColor DarkGray

if ($UseDocker) {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) { throw "未安装 Docker，请先安装 Docker Desktop 或将 useDocker 设为 false" }
    Ensure-DockerSql
}

$renderedSql = New-RenderedSql
$sqlcmd = Find-SqlCmd
$server = if ($SqlPort -eq 1433) { $SqlHost } else { "$SqlHost,$SqlPort" }

try {
    if ($sqlcmd) {
        Write-Ok "使用 sqlcmd: $sqlcmd"
        if (-not $SkipWait) {
            Wait-SqlReady -SqlCmdPath $sqlcmd -Server $server -User $SaUser -Password $SaPassword
        }
        Write-Step "执行建库脚本..."
        & $sqlcmd -S $server -U $SaUser -P $SaPassword -C -i $renderedSql -b
        if ($LASTEXITCODE -ne 0) { throw "sqlcmd 执行失败，exit=$LASTEXITCODE" }
    }
    elseif ($UseDocker) {
        Write-Warn "本机无 sqlcmd，改用 docker exec 执行"
        $cid = docker ps -qf "ancestor=mcr.microsoft.com/mssql/server:2022-latest"
        if (-not $cid) { $cid = "wms-deploy-sqlserver-1" }
        if (-not $SkipWait) {
            Write-Step "等待容器内 SQL Server..."
            $ready = $false
            for ($i = 0; $i -lt 30; $i++) {
                docker exec $cid /opt/mssql-tools18/bin/sqlcmd -S localhost -U $SaUser -P $SaPassword -C -Q "SELECT 1" 2>$null | Out-Null
                if ($LASTEXITCODE -eq 0) { $ready = $true; break }
                docker exec $cid /opt/mssql-tools/bin/sqlcmd -S localhost -U $SaUser -P $SaPassword -C -Q "SELECT 1" 2>$null | Out-Null
                if ($LASTEXITCODE -eq 0) { $ready = $true; break }
                Start-Sleep -Seconds 3
            }
            if (-not $ready) { throw "容器内 SQL Server 未就绪" }
            Write-Ok "容器 SQL Server 已就绪"
        }
        Get-Content $renderedSql -Raw -Encoding UTF8 | docker exec -i $cid /opt/mssql-tools18/bin/sqlcmd -S localhost -U $SaUser -P $SaPassword -C
        if ($LASTEXITCODE -ne 0) {
            Get-Content $renderedSql -Raw -Encoding UTF8 | docker exec -i $cid /opt/mssql-tools/bin/sqlcmd -S localhost -U $SaUser -P $SaPassword -C
            if ($LASTEXITCODE -ne 0) { throw "docker exec sqlcmd 失败" }
        }
    }
    else {
        throw @"
未找到 sqlcmd，且未启用 Docker。

可选方案：
  1) 安装 SQL Server 命令行工具后重试
  2) 编辑 init-wms-db.config.json 将 useDocker 设为 true，或执行: .\scripts\init-wms-db.ps1 -UseDocker
  3) 用 SSMS 打开生成后的 SQL（模板 scripts\sql\init-wms-db.sql），连到 master 执行
"@
    }
}
finally {
    if (Test-Path $renderedSql) { Remove-Item $renderedSql -Force -ErrorAction SilentlyContinue }
}

Write-Host ""
Write-Ok "建库完成"
Write-Host @"

连接信息（请同步到 application-dev.yml / 环境变量）：
  jdbc:sqlserver://${SqlHost}:${SqlPort};databaseName=${Database};encrypt=true;trustServerCertificate=true
  username: $AppUser
  password: $AppPassword

修改账号密码：编辑 scripts\init-wms-db.config.json 后重新执行本脚本。

下一步：
  cd wms-backend
  .\start.bat
  # Flyway 自动建表；默认登录 admin / 123456
"@ -ForegroundColor White
