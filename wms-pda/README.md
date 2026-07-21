# WMS PDA 移动端 (uni-app Vue3)

PDA 仓储作业端，支持入库、出库、盘点、质检、库存查询。

## API 模块

所有 PDA 接口统一封装在 `src/api/mobile.js`，覆盖 API 文档 13.x 全部端点：

| 模块 | 接口 |
|------|------|
| 认证 | 移动登录、刷新Token、用户信息、改密 |
| 任务 | GET /mobile/tasks |
| 入库 | 详情、扫码、批量扫码、完成 |
| 出库 | 详情、FIFO推荐、扫码、完成 |
| 移库 | POST /mobile/transfer |
| 库存 | GET/POST /mobile/inventory/query |
| 盘点 | 详情、扫码、盘盈、确认空、完成 |
| 质检 | 详情、提交结果 |
| 追溯 | POST /mobile/trace |
| 同步 | POST /mobile/sync |
| 消息 | 列表、未读数、已读、全部已读 |
| 条码 | POST /mobile/barcode/parse |

## 快速启动（H5 调试）

```bash
cd wms-pda
npm install
npm run dev:h5
```

浏览器访问 **http://localhost:5174**（若端口占用会自动换端口，请看终端输出）。

> 项目源码在 `src/` 目录，请用 `npm run dev:h5` 启动，不要混用根目录下的 HBuilderX 默认模板文件。

## 真机 / Android

1. 修改 `src/utils/config.js` 中 `baseUrl` 为电脑局域网 IP，例如 `http://192.168.0.214:9980/api/v1`
2. 使用 HBuilderX 打开本目录，运行到 Android 设备
3. 或使用 `npm run dev:app` / `npm run build:app`

## 默认账号

- 用户名: `admin`
- 密码: `123456`

## 功能模块

- [x] PDA 登录（deviceNo）
- [x] 待办任务首页（入库/出库/盘点/质检）
- [x] 扫码入库
- [x] 扫码出库
- [x] 库存查询
- [x] 盘点作业
- [x] 质检录入
- [x] 离线同步（框架）
- [x] 个人中心 / 退出
