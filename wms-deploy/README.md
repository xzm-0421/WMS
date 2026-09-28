# YM_MES / WMS 部署说明（Docker Compose）

## 目录内容

```
wms-deploy/
├── docker-compose.yml   # wms-api + SQL Server + Redis + Nginx
├── nginx/nginx.conf     # 前端静态资源 + /api 反向代理
└── README.md
```

## 1. 前置准备

| 组件 | 版本 |
|---|---|
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| Docker / Docker Compose | 24+ |

## 2. 构建产物

```bash
# 后端 Jar（Dockerfile 期望 target/wms-backend-1.0.0-SNAPSHOT.jar）
cd wms-backend
mvn package -DskipTests

# 前端静态资源（nginx 挂载 ../wms-web/dist）
cd ../wms-web
npm install
npm run build
```

## 3. 启动

```bash
cd wms-deploy
docker compose up -d
# 访问 http://<服务器IP>/
```

服务与端口：

| 服务 | 端口 | 说明 |
|---|---|---|
| nginx | 80 | 对外入口（前端 + /api 代理） |
| wms-api | 9980 | Spring Boot API（默认不直接对外） |
| sqlserver | 1433 | SQL Server 2022 |
| redis | 6379 | 可选，见第 4 节 |

## 4. Redis（可选）与 MES 分布式工序锁

wms-api 默认以 `prod` profile 启动，**不使用 Redis**（验证码/PDA 短缓存/MES 工序锁回退进程内存），适配无 Redis 的单机部署。

多实例部署或希望启用分布式工序锁时，加上 `redis` profile：

```bash
# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE = "prod,redis"
docker compose up -d

# Linux
SPRING_PROFILES_ACTIVE=prod,redis docker compose up -d
```

`redis` profile 会读取以下环境变量（见 `wms-backend/src/main/resources/application-redis.yml`）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `REDIS_HOST` | localhost（compose 内为 `redis`） | Redis 地址 |
| `REDIS_PORT` | 6379 | 端口 |
| `REDIS_PASSWORD` | 空 | 密码 |
| `REDIS_DB` | 0 | 库号 |
| `REDIS_CONNECT_TIMEOUT` | 1s | 连接超时（保持短，故障快速回退） |
| `REDIS_TIMEOUT` | 2s | 命令超时 |
| `MES_LOCK_MODE` | AUTO | 工序锁模式：`AUTO`/`REDIS`/`LOCAL` |

**锁行为**：`AUTO` 下，有 Redis 用 Redis 分布式锁（Lua 释放、看门狗按租期 1/3 续约），Redis 缺失或运行异常时自动回退进程内锁并告警。仅单实例运行时无需 Redis。

Docker 内 Redis 密码通过 `.env` 或环境变量 `REDIS_PASSWORD` 注入，需与 wms-api 保持一致。

## 5. 关键环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | prod | 设为 `prod,redis` 启用 Redis |
| `SQLSERVER_SA_PASSWORD` | sa@2019 | SQL Server sa 密码（生产务必修改） |
| `REDIS_PASSWORD` | 空 | Redis 密码（启用 Redis 时建议设置） |
| `WMS_PUBLIC_BASE_URL` | http://localhost:9980 | 金蝶标签打印回访地址 |

## 6. 多实例伸缩说明

`nginx.conf` 的 upstream 使用 `server wms-api:9980;`。开源版 Nginx 在启动时解析并缓存该地址，直接 `docker compose up --scale wms-api=2` **不会**自动负载均衡到多副本。需要多实例时，请改为以下任一方案：

- 使用外部负载均衡（Traefik / HAProxy / 云 LB）指向多个 wms-api 副本；
- 或为 nginx 配置动态 DNS 解析（NGINX Plus 的 `resolve`，或配合 `resolver 127.0.0.11` 的第三方方案）。

多实例前请确认：已启用 Redis（`prod,redis`），以便 MES 工序锁生效。

## 7. 生产注意事项

- 修改 `application.yml` 中的 `wms.jwt.secret`，勿使用默认值。
- 修改 SQL Server `sa` 密码与 Redis 密码，避免使用默认值。
- 数据库结构由 Flyway 自动迁移（`db/migration/sqlserver`），请勿手工改库。
- 备份 `sqlserver_data` 与 `redis_data` 卷。
