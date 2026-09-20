@echo off
setlocal
cd /d "%~dp0.."

echo [1/2] Building wms-web ...
cd wms-web
call npm run build
if errorlevel 1 (
  echo.
  echo Frontend build failed. See errors above.
  echo.
  pause
  exit /b 1
)

echo [2/2] Copying dist to wms-backend\src\main\resources\static ...
cd /d "%~dp0.."
set "STATIC_DIR=wms-backend\src\main\resources\static"
if exist "%STATIC_DIR%" rmdir /s /q "%STATIC_DIR%"
mkdir "%STATIC_DIR%"
xcopy /e /i /y "wms-web\dist\*" "%STATIC_DIR%\" >nul
if errorlevel 1 (
  echo.
  echo Copy failed.
  echo.
  pause
  exit /b 1
)

echo.
echo Done. Start backend and open: http://localhost:9980/
echo   cd wms-backend ^&^& start.bat
echo.
pause
endlocal
