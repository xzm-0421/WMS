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

### B1. 平板现场作业（场景一 / US3.2）

- 重写 `MES-tablet/src/pages/station/station.vue`：工序检索 → `GET /mobile/mes/reports/context` → 选工序/设备/操作员 → 输入重量/数量 → `POST /mobile/mes/reports`。
- 新建 `MES-tablet/src/api/mes.js`，复用 `src/utils/http.js`，对齐 `MES-mobile/src/api/mes.js`。
- 新增 `src/utils/scale.js` 抽象层（当前实现为手动输入；预留蓝牙/串口适配接口与 `utils/config.js` 的 `scale` 配置）。

### B2. MES 框料标签（新建模板 + 蓝牙打印）

- 后端新增 `com.wms.mes.label` 包：
  - 新增 `GET/POST /mobile/mes/labels`（报工成功后按框生成标签数据：工单/物料/工序/重量/条码）。
  - 新增 MES 专用标签模板与生成器。
- 平板 `print-panel` 6 个占位框接真实框数；“标签打印”调用蓝牙打印（uni-app 蓝牙/打印插件），失败可重试。
- Flyway 新增 `V1_0_48__mes_label.sql`（H2 + SQLServer 双份）记录打印任务（如确需留痕）。

### B3. 后端支撑

- 报工提交复用现有 `MesReportSubmitRequest.weightKg`；不新建称重表（后续接秤再评估 `mes_weighing`）。
- 冲压称重工序判定：`mes_process` 增“是否称重工序”标识或按工序编码配置。

## 3. 阶段 C：可靠性与安全非功能（需求 §4、§5）

- **C1** 客户端离线：移动端/平板改本地 SQLite（现为 storage 队列 `offlineQueue.js`），补冲突处理 UI。
- **C2** 安全：ERP IP 白名单、API 签名、敏感字段 AES-256、密码强度+90 天过期、操作日志查询页。
- **C3** 数据埋点：§5.2 的 8 个事件。
- **C4** 告警通道：队列满 / 5 分钟 SLA / 长期未同步（现 `sendAlert` 仅日志）。
- **C5** MES↔ERP 强制 HTTPS/TLS、本地每日备份、按车间数据隔离。

## 4. 里程碑与工时

| 阶段 | 内容 | 工时 |
|------|------|------|
| A | 转移列表 / 同步中心 / 首页 / 返工图谱 / 测试 | 1–1.5 周 |
| B | 平板现场 + 手动称重 + 蓝牙标签 | 2–3 周 |
| C | 离线 / 安全 / 埋点 / 告警 | 2–3 周 |

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

- [ ] B1.1 新建 `MES-tablet/src/api/mes.js`
- [ ] B1.2 重写 `station.vue`：工序检索 + 报工上下文加载
- [ ] B1.3 报工表单：工序 / 设备 / 操作员 / 重量 / 数量 / 提交
- [ ] B1.4 新增 `utils/scale.js` 抽象层 + `config.js` 的 `scale` 配置
- [ ] B2.1 后端新增 `GET/POST /mobile/mes/labels`
- [ ] B2.2 新增 MES 专用标签模板与生成器
- [ ] B2.3 Flyway `V1_0_49__mes_label.sql`（H2 + SQLServer；V1_0_48 已被出站队列占用）
- [ ] B2.4 平板 `print-panel` 接真实框数 + 蓝牙打印
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
- [ ] C2.4 密码强度 + 90 天过期
- [ ] C2.5 操作日志查询页
- [ ] C3.1 埋点 §5.2 八个事件
- [ ] C4.1 告警通道（队列满 / SLA / 长期未同步）
- [ ] C5.1 HTTPS/TLS、每日备份、车间数据隔离

---

*文档结束*
