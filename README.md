# WMS 仓储管理系统

企业制造仓储管理系统（WMS），包含 Web 管理后台、PDA 移动端、Java 后端 API。

## 项目结构

```
WMS/
├── wms-backend/     # Spring Boot 3.2 后端 API
├── wms-web/         # Vue 3 + Element Plus 管理后台
├── wms-pda/         # uni-app PDA 移动端
├── wms-deploy/      # Docker Compose + Nginx 部署
├── WMS开发设计文档.md
└── docs/            # 原始需求文档
```

## 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | **17+** | 已配置：`D:\java\jdk-17` |
| Maven | 3.9+ | 编译后端必需 |
| Node.js | 18+ | 前端开发 |
| HBuilderX | 最新版 | PDA 开发 |
| Docker | 24+ | 生产部署（可选） |

### 环境变量（建议配置）

```
JAVA_HOME=D:\java\jdk-17
PATH=%JAVA_HOME%\bin;%PATH%
```

> 当前机器若为 Java 8，请确保 `java -version` 显示 17.0.14 后再启动后端。

## 快速启动（本地开发）

### 0. 一键创建数据库（首次必做）

先按需编辑账号密码配置：`scripts/init-wms-db.config.json`（`saPassword` / `appUser` / `appPassword` / `database` 等）。

本机已装 SQL Server：

```bat
scripts\init-wms-db.bat
```

或用 Docker 起 SQL Server 再建库（也可在配置里设 `"useDocker": true`）：

```powershell
.\scripts\init-wms-db.ps1 -UseDocker
```

脚本按配置创建库与应用登录。随后启动后端，**Flyway 自动建表**。配置变更后请同步 `application-dev.yml` / 环境变量中的数据源账号。

### 1. 一键启动（前端 + 后端，同端口 9980）

```bash
# 仓库根目录双击 start.bat
# 或：
cd wms-backend
# 双击 start.bat（会把 wms-web 打进后端 static，再启动 Spring Boot）
```

- **管理后台**: http://localhost:9980/  
- **API**: http://localhost:9980/api/v1  
- **Swagger**: http://localhost:9980/doc.html  
- 开发环境使用 **SQL Server**（本地库名 `wms`）
- 默认连接：`localhost:1433`，账号 `wms_app` / `Wms@123456`（见 `application-dev.yml`）

**默认账号**: `admin` / `123456`

> Vite 与后端不能同时占用 9980，因此日常启动是「前端打包进后端、只起 9980」。  
> 改过前端后若未自动重建：设 `FORCE_WEB_BUILD=1` 再运行 `start.bat`，或执行 `scripts\sync-web-to-backend.bat`。  
> 需要前端热更新时：另开 `wms-web\start.bat`（5173），并保证后端已在 9980。

### 2. Web 管理后台（可选）

**方式 A：只同步静态资源（不启服务）**

```bat
scripts\sync-web-to-backend.bat
```

或：

```bash
cd wms-web
npm install
npm run build:backend
```

**方式 B：前端热更新（5173）**

```bash
cd wms-web
npm install
npm run dev
```

- 访问: http://localhost:5173
- 已配置 API 代理到 9980

### 3. PDA 移动端

1. 用 **HBuilderX** 打开 `wms-pda` 目录
2. 修改 `utils/config.js` 中的 `baseUrl` 为电脑局域网 IP
3. 运行到 Android 设备或模拟器

### 4. Docker 生产部署

```bash
cd wms-deploy
# 先构建后端 JAR 和前端 dist
cd ../wms-backend && mvn package -DskipTests
cd ../wms-web && npm run build
cd ../wms-deploy
docker compose up -d
```

## 已实现功能（Phase 0/1）

### 后端
- [x] 统一响应 / 全局异常 / JWT 认证
- [x] Web 登录 + 验证码、PDA 登录
- [x] Flyway 数据库迁移（H2 / SQL Server）
- [x] 物料 CRUD、库存查询
- [x] 库存增减服务 + 流水记录
- [x] PDA 待办任务、扫码入库
- [x] 演示数据（仓库、库位、物料、入库单 RK202606250001）

### Web 前端
- [x] 登录页、主布局、工作台
- [x] 物料管理、实时库存

### PDA
- [x] 登录、任务首页、扫码入库框架

## 待开发（Phase 2+）

- 出库管理 + FIFO 推荐
- 盘点、质检、生产对接
- 条码引擎、报表分析
- 离线同步、消息中心

## API 规范

- Base URL: `/api/v1`
- 认证: `Authorization: Bearer {token}`
- 响应: `{ code, message, data, timestamp }`

详见 `WMS_API接口文档.docx` 与 `WMS开发设计文档.md`。

## 演示入库流程

1. 启动后端 + Web
2. Web 登录 admin/123456 → 查看「实时库存」（初始为空）
3. PDA 登录 → 任务首页看到 `RK202606250001`
4. 进入扫码入库 → 点击「扫码入库」完成收货
5. Web「实时库存」可看到库存增加

---

版本 V1.0 | 2026-06-25
