@echo off
chcp 65001 >nul
echo ============================================
echo   MES-Tablet 手机访问配置工具
echo ============================================
echo.

echo [1/4] 检查本机 IP 地址...
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    set LOCAL_IP=%%a
    goto :found_ip
)
:found_ip
echo ✅ 检测到本机 IP: %LOCAL_IP%
echo.

echo [2/4] 配置防火墙规则（开发端口 5175）...
netsh advfirewall firewall add rule name="MES-Tablet Dev Server" dir=in action=allow protocol=TCP localport=5175 >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ 端口 5175 已开放（前端开发服务器）
) else (
    echo ⚠️ 端口 5175 配置失败，请手动开放
)
echo.

echo [3/4] 配置防火墙规则（后端 API 端口 9980）...
netsh advfirewall firewall add rule="MES-Backend API" dir=in action=allow protocol=TCP localport=9980 >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ 端口 9980 已开放（后端API服务）
) else (
    echo ⚠️ 端口 9980 配置失败，请手动开放
)
echo.

echo [4/4] 生成访问信息...
echo.
echo ============================================
echo   📱 手机访问配置完成！
echo ============================================
echo.
echo   前端地址: http://%LOCAL_IP%:5175
echo   后端地址: http://%LOCAL_IP%:9980
echo.
echo   请确保：
echo   1. 手机和电脑在同一 WiFi 网络
echo   2. 后端服务已启动（端口 9980）
echo   3. 前端服务已重启（端口 5175）
echo.
echo   如果仍无法访问，请检查：
echo   - Windows 防火墙是否允许以上端口
echo   - 杀毒软件是否阻止了连接
echo   - 手机是否关闭了代理/VPN
echo.
echo ============================================

pause