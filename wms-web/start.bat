@echo off
REM 仅前端热更新开发（端口 5173，API 代理到 9980）
REM 日常请用仓库根目录 / wms-backend 的 start.bat，页面与 API 均在 9980
cd /d %~dp0
npm run dev
