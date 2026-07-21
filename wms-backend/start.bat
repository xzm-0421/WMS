@echo off
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

cd /d %~dp0
mvn spring-boot:run -DskipTests
