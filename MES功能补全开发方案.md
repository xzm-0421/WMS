# YM_MES 功能补全开发方案（纯 MES）

> 版本: v1.1 | 依据: 《轻MES需求规格说明书 R1.0》US1–US5 + 代码核查
> 决策: 电子秤暂用手动输入；框料标签新建 MES 专用模板与接口，蓝牙打印；优先阶段 A
> 范围: 仅轻 MES（不含 WMS 侧模块）

## 0. 现状结论

MES 主体链路（基础资料 → 工序计划 → 报工 → 转移 → 返工 → 同步）已闭环。
本次范围仅限轻 MES，缺口集中在：终端现场作业（平板 / 称重 / 标签）、转移记录 UI、
同步中心聚合、移动端首页数据，以及离线与安全非功能项。

## 1. 阶段 A：MES 现有能力补全（无硬件依赖，约 1–1.5 周）

### A1. 工序转移记录列表 + 重试（US4.3）

- 新增 `wms-web/src/views/mes/TransferList.vue`，调用已有 `getMesTransfers` / `retryMesTransfer`（`src/api/mes.ts:306-312`）。
- `src/router/index.ts` 增加 `mes/transfers`；`src/layouts/MainLayout.vue` 轻MES 菜单加入口。
- `PermissionCatalog.java:103` 后增加 `MES_TRANSFER_LIST("mes:transfer:list", ...)`；`src/utils/permission.ts` 同步映射。
- 移动端 `MES-mobile/src/pages/report/records.vue` 增加“转移”页签，消费未使用的 `getTransfers/retryTransfer`。

### A2. 同步中心聚合（US4.6）

- 后端 `MesSyncWorkerService` / `MesReworkSyncController` 扩展 `GET /mes/sync/panel`，返回 plan / transfer / defect 的待同步与失败计数（现仅 `mes_report`）。
- 前端 `wms-web/src/views/mes/SyncCenter.vue` 增加单据类型筛选 + 计划/转移“重试”按钮。
- 增加 `mes:transfer:retry` 权限码（`mes:plan:retry` 已有）。

### A3. 移动端首页统计与待办

- 新增 `GET /mobile/mes/summary`：今日报工数、待同步/失败数、网络状态、未完成工序计划数、未处理不良数。
- `MES-mobile/src/pages/home/home.vue` 绑定真实数据（现 6 个 tile 硬编码 0，`todos` 为空）。
- `service.vue` 的“报表/大屏”按需接入或移除 `comingSoon`。

### A4. 返工序列可视化与状态机（US5.7，Should）

- `wms-web/src/views/mes/ReworkView.vue` 增加返工路径图谱（节点=工序，按 opStatus 着色）。
- `MesReworkService` 补状态流转：`无返工 → 返工中 → 返工完成 → 二次返工中 → 已关闭`。

### A5. 测试与文档

- `wms-backend/src/test/java/com/wms/mes/` 补同步中心聚合与状态机单测。
- 更新 `README.md` 已实现功能清单。

## 2. 阶段 B：MES 现场作业落地（平板 + 标签，约 2–3 周）

> 决策：重量先手动输入，代码预留秤适配口；框料标签新建 MES 模板 + 蓝牙打印。
> 实现现状：平板称重/打印**初版已由组员完成**（提交 `32fe3cc`「完善平板端的称重打印功能」，2026-09-24）。当前为可用雏形，仍有关键缺口待补。

### B0. 组员已完成（提交 32fe3cc）

- `MES-tablet/src/pages/station/station.vue`：工单/工序计划列表（`GET /mes/plans`）→ 选工单 → 手动输入重量「确认称重」→ `POST /mobile/mes/reports` → 加入打印队列 →「打印」调 `POST /mobile/print/label/resolve`。
- 新增 `MES-tablet/src/api/mes.js`（`getMesPlanList/getReportContext/submitReport/printLabel`），复用 `utils/http.js`（含 `withDevice`）。
- 显示设置、模拟数据模式（无数据/异常时回退 `MOCK_PLAN_DATA`）；`utils/config.js`、`index.html`、`pages.json` 微调。

### B1. 平板现场作业（待补全 / 修复）

- [ ] 修复报工类型：`reportType` 由非法值 `WEIGH` 改为 `NORMAL`（后端仅认 `NORMAL/REWORK`；`WEIGH` 会绕过「正常工序已完成」校验）。
- [ ] 数量来源：去掉 `qty = floor(weight*4)` 硬编码，改为手工输入或按规则/配置换算。
- [ ] 设备与工序：去掉 `equipmentCode='SCALE-001'`、`processCode||'DEFAULT'` 硬编码；改用 `GET /mobile/mes/reports/context` 选择工序/设备/操作员。
- [ ] 检索：`/mes/plans` 不支持 `keyword`，改用 `moNo/productCode/status` 过滤。
- [ ] 生产防护：移除或用开关限制 `MOCK_PLAN_DATA` 回退（生产环境禁止模拟数据）。
- [ ] 新增 `MES-tablet/src/utils/scale.js` 抽象层 + `utils/config.js` 的 `scale` 配置（当前手动输入，预留蓝牙/串口适配口）。

### B2. MES 框料标签（新建模板 + 蓝牙打印）

- 现状：平板调用的是 **WMS** 的 `POST /mobile/print/label/resolve`，其 DTO `MobileLabelResolveRequest` 仅接收 `barcodeContent/warehouseCode`，平板传入的 `reportNo/moNo/weightKg/qty` 被忽略——**接口不匹配，需替换**。
- [ ] 后端新增 `com.wms.mes.label` 包：`GET/POST /mobile/mes/labels`（报工后按框生成标签数据：工单/物料/工序/重量/条码）+ MES 专用模板生成器。
- [ ] 平板 `print-panel` 由报工记录改为**按框**列表；接入蓝牙打印（uni-app 打印插件），失败可重试。
- [ ] Flyway `V1_0_49__mes_label.sql`（H2 + SQLServer；`V1_0_48` 已被出站队列占用）。

### B3. 后端支撑

- 报工提交复用现有 `MesReportSubmitRequest.weightKg`；不新建称重表。
- [ ] 冲压/称重工序判定：`mes_process` 增「是否称重工序」标识或按工序编码配置。

### B4. 联调与验收

- [ ] 平板 + 电子秤（手动→自动）+ 蓝牙打印机 + 金蝶联调。
- [ ] 阶段 B 验收。

## 3. 阶段 C：可靠性与安全非功能（需求 §4、§5）

- **C1** 客户端离线：移动端/平板改本地 SQLite（现为 storage 队列 `offlineQueue.js`），补冲突处理 UI。
- **C2** 安全：ERP IP 白名单、API 签名、敏感字段 AES-256、密码强度+90 天过期、操作日志查询页。
- **C3** 数据埋点：§5.2 的 8 个事件。
- **C4** 告警通道：队列满 / 5 分钟 SLA / 长期未同步（现 `sendAlert` 仅日志）。
- **C5** MES↔ERP 强制 HTTPS/TLS、本地每日备份、按车间数据隔离。
- **D** 商用许可：双许可（部署许可 + 账号授权）、许可码在线激活（Ed25519/RSA 非对称签名）、未授权只读并拦截全部写操作、SUPER_ADMIN 豁免、存量仅超管授权。

## 4. 里程碑与工时

| 阶段 | 内容 | 工时 |
|------|------|------|
| A | 转移列表 / 同步中心 / 首页 / 返工图谱 / 测试 | 1–1.5 周 |
| B | 平板现场 + 手动称重 + 蓝牙标签 | 2–3 周 |
| C | 离线 / 安全 / 埋点 / 告警 | 2–3 周 |
| D | 商用许可（双许可 / 许可码激活 / 未授权只读） | 1–1.5 周 |

## 5. 风险与依赖

- 蓝牙标签打印机型号/指令集待确认，影响 B2 联调。
- 返工报工单 / 转移单金蝶 API 契约待确认（需求 Q5）。
- 平板与真实金蝶环境联调依赖现场。

## 6. 验收要点

- **A**：转移可查可重试；同步中心能看到全部单据失败并重试；首页统计与库中数据一致。
- **B**：平板选工单 → 输入重量 → 报工 → 生成并蓝牙打印框料标签 → 报工记录可查。
- **C**：断网继续报工、恢复自动同步；安全项逐条可验证。

---

## 7. 阶段任务 Todo 清单（执行跟踪）

### 阶段 A — MES 现有能力补全

- [x] A1.1 后端 `PermissionCatalog` 增加 `mes:transfer:list`、`mes:transfer:retry`
- [x] A1.2 前端 `utils/permission.ts` 增加 `/mes/transfers` 权限映射
- [x] A1.3 新增 `wms-web/src/views/mes/TransferList.vue`（列表 / 详情 / 重试）
- [x] A1.4 `router/index.ts` 注册 `mes/transfers`
- [x] A1.5 `MainLayout.vue` 轻MES 菜单增加“工序转移记录”
- [x] A1.6 移动端 `records.vue` 增加“转移”页签并消费 `getTransfers/retryTransfer`
- [x] A2.1 扩展 `GET /mes/sync/panel` 聚合报工/转移分类型计数
- [x] A2.2 `SyncCenter.vue` 增加类型筛选与重试按钮
- [x] A3.1 新增 `GET /mobile/mes/summary`
- [x] A3.2 `home.vue` 绑定真实统计（今日报工/待同步/失败/未完成计划/未处理不良/网络）
- [ ] A3.3 `service.vue` 报表/大屏入口处理（接入或移除 comingSoon）
- [x] A4.1 `ReworkView.vue` 返工路径图谱（el-steps）
- [x] A4.2 `MesReworkService` 返工状态机流转（全手动 complete/secondary/close）
- [x] A5.1 补 MES 单测（`MesReworkStatusRuleTest` 等；全量 93 通过）
- [ ] A5.2 更新 `README.md`
- [ ] A6.1 阶段 A 联调与验收

### 批次二 — 不良单 / 返工工单回传 ERP（已实现，FormId 待配置）

- [x] 出站队列表 `mes_erp_outbox`（Flyway `V1_0_48`，SQLServer + H2）
- [x] 实体/Mapper、Builder（`MesDefectSaveBuilder` / `MesReworkOrderSaveBuilder`）
- [x] `KingdeeCloudProperties` 增加不良单/返工工单 FormId 与字段（占位，留空则标记人工介入）
- [x] `MesSyncWorkerService.processOutbox()`（CREATE→`rawSave`；SUBMIT→`submitAndAuditExistingBill`）
- [x] 创建不良单入队 CREATE；`完成返工` 入队 SUBMIT
- [x] `/mes/sync/panel` 增加不良/返工计数；`GET /mes/sync/outbox`、`POST /mes/sync/outbox/{id}/retry`
- [x] 前端 `SyncCenter.vue` 不良/返工区块；`ReworkView.vue` 同步状态列 + 同步重试
- [ ] 现场提供金蝶不良单/返工工单 FormId 与字段并联调

### 阶段 B — 平板现场 + 手动称重 + 蓝牙标签

> 初版由组员完成（提交 32fe3cc）

- [x] B1.1 新建 `MES-tablet/src/api/mes.js`（组员）
- [x] B1.2 `station.vue` 工单列表 + 工序计划检索（组员；改用 context 待补）
- [x] B1.3 报工表单：手动重量 + 提交报工（组员；工序/设备/操作员待补）
- [ ] B1.4 修复 `reportType`(WEIGH→NORMAL) / 数量 / 设备编码硬编码
- [ ] B1.5 改用 `/mobile/mes/reports/context` 选择工序/设备/操作员
- [ ] B1.6 移除生产环境 `MOCK_PLAN_DATA` 回退（或加开关）
- [ ] B1.7 新增 `utils/scale.js` 抽象层 + `config.js` 的 `scale` 配置
- [ ] B2.0 替换不匹配的 `/mobile/print/label/resolve` 调用
- [ ] B2.1 后端新增 `GET/POST /mobile/mes/labels`
- [ ] B2.2 新增 MES 专用标签模板与生成器
- [ ] B2.3 Flyway `V1_0_49__mes_label.sql`（H2 + SQLServer）
- [ ] B2.4 平板 `print-panel` 按框 + 蓝牙打印
- [ ] B3.1 冲压/称重工序标识（`mes_process`）
- [ ] B4.1 平板 + 蓝牙打印机 + 金蝶联调
- [ ] B5.1 阶段 B 验收

### 阶段 C — 可靠性与安全非功能

- [ ] C1.1 移动端离线 SQLite
- [ ] C1.2 平板离线 SQLite
- [ ] C1.3 冲突处理 UI
- [ ] C2.1 ERP IP 白名单
- [ ] C2.2 API 签名校验
- [ ] C2.3 敏感字段 AES-256 加密
- [x] C2.4 密码强度 + 90 天过期
- [x] C2.5 操作日志查询页
- [x] C3.1 埋点 §5.2 八个事件
- [x] C4.1 告警通道（队列满 / SLA / 长期未同步）
- [ ] C5.1 HTTPS/TLS、每日备份、车间数据隔离

### 阶段 D — 商用许可（双许可 / 许可码激活 / 未授权只读）

- [ ] D0.1 Flyway `V1_0_55`：`sys_user.licensed/license_expire_at`（存量仅 SUPER_ADMIN 授权）+ 新表 `sys_license`（h2 + SQLServer）
- [ ] D1.1 `LicenseCodec`（Ed25519/RSA 载荷+签名编解码，内置公钥验签）+ 厂商签发工具 + `LicenseCodecTest`
- [ ] D2.1 `LicenseService` + `SysLicense`（`current/activate/isDeploymentValid/revoke`，缓存与定时刷新）
- [ ] D2.2 登录/`userinfo` 返回 `licensed/readonly/deploymentLicensed/licenseExpireAt/machineCode`
- [ ] D3.1 `LicenseReadonlyFilter`（全部写操作拦截 + 白名单 + SUPER_ADMIN 豁免）
- [ ] D3.2 前端只读 banner + `request` 统一处理 + 写按钮禁用
- [ ] D4.1 `views/system/LicenseManage.vue`（机器码/状态/到期/激活/吊销）
- [ ] D4.2 `UserList.vue` 授权开关/到期日 + `PUT /system/users/{id}/license`
- [ ] D4.3 权限码 `system:license:list` / `system:license:manage` + 路由/菜单映射
- [ ] D5.1 配置 `wms.license.*`（`enabled/public-key/grace-days/product/edition`）
- [ ] D6.1 单测 `LicenseCodecTest` / `LicenseServiceTest` + 构建验证
- [ ] D7.1 阶段 D 验收（未激活 / 激活 / 过期 / 未授权 / 超管 / 开关）

---

*文档结束*
