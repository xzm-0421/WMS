@echo off
setlocal
set JAVA_HOME=D:\java\jdk-17
set MAVEN_HOME=D:\tools\apache-maven-3.9.6
set SQLSERVER_HOST=localhost
set SQLSERVER_PORT=1433
set SQLSERVER_DB=wms
set SQLSERVER_USER=wms_app
set SQLSERVER_PASSWORD=Wms@123456
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

echo JAVA_HOME=%JAVA_HOME%
java -version

where mvn >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未找到 Maven，请先安装 Apache Maven 3.9+
    echo 下载: https://maven.apache.org/download.cgi
    echo 解压后将 bin 目录加入 PATH
    pause
    exit /b 1
)

cd /d "%~dp0"
set "ROOT=%~dp0.."
set "WEB_DIR=%ROOT%\wms-web"
set "STATIC_INDEX=%~dp0src\main\resources\static\index.html"

echo.
echo ========================================
echo   WMS 启动：前端 + 后端同端口 9980
echo ========================================
echo.

REM 前端打包进后端 static，浏览器只访问 http://localhost:9980/
REM （Vite 与 Spring 不能同时占用 9980；热更新请另开 wms-web\start.bat 用 5173）
if /I "%SKIP_WEB_SYNC%"=="1" (
    echo [前端] 已跳过同步 ^(SKIP_WEB_SYNC=1^)
    goto :backend
)

if not exist "%WEB_DIR%\package.json" (
    echo [警告] 未找到 wms-web，仅启动后端
    goto :backend
)

where npm >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [警告] 未找到 npm，跳过前端同步
    goto :backend
)

if not exist "%WEB_DIR%\node_modules\" (
    echo [前端] 首次安装依赖 npm install ...
    pushd "%WEB_DIR%"
    call npm install
    if errorlevel 1 (
        echo [错误] npm install 失败
        popd
        pause
        exit /b 1
    )
    popd
)

if exist "%STATIC_INDEX%" if /I not "%FORCE_WEB_BUILD%"=="1" (
    echo [前端] 已有 static\index.html，跳过重建 ^(改前端后请设 FORCE_WEB_BUILD=1 或运行 scripts\sync-web-to-backend.bat^)
    goto :backend
)

echo [1/2] 构建并同步 wms-web -^> 后端 static ...
pushd "%ROOT%"
call node scripts\sync-web-to-backend.mjs
if errorlevel 1 (
    echo [错误] 前端同步失败
    popd
    pause
    exit /b 1
)
popd

:backend
echo [2/2] 启动后端 wms-backend ...
echo.
echo   管理后台 + API 同端口: http://localhost:9980/
echo   API:                    http://localhost:9980/api/v1
echo   Swagger:                http://localhost:9980/doc.html
echo.
echo   关闭本窗口即停止服务。
echo.

mvn spring-boot:run -DskipTests
endlocal
