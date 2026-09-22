# WMS 需求规格说明书 vs 当前项目 — 逐条差异对照

> **需求基线**：`WMS需求规格说明书.docx` V1.0 / 2026-06-25
> **对照时间**：2026-09
> **判定方式**：基于代码静态检查（控制器 / 路由 / 页面 / Flyway 迁移脚本），个别项需运行确认
> **图例**：✅ 已实现　🟡 部分实现 / 已开发未接线　❌ 未实现　⭐ 超出需求文档

---

## 0. 总体结论

当前仓库已**明显超出**需求文档 V1.0 的范围：文档只规划到 WMS 的 PDA + Web + 后端三层，而项目额外实现了**金蝶 ERP 深度集成**、**轻 MES**、**收料/备料/拣配发料/委外全流程**，以及平板端 `MES-tablet`、手机端 `MES-mobile`。

WMS 主线大体完成，主要问题集中在两类：

1. **已开发但未接线**：出库单、调拨、其他出入库、盘点计划等页面已写好，但未注册路由/菜单，用户不可达。
2. **需求内模块缺失**：质检闭环、基础数据（分类/客户/单位/字典/组织）、系统管理（操作日志/字典/配置）、分析报表（周转/呆滞/库龄/绩效/保质期）、系统监控。

---

## A. PDA 端（需求 §5）

| 编号 | 需求项 | 状态 | 证据 / 说明 |
|---|---|---|---|
| 5.2 | 登录认证 | ✅ | `wms-pda/src/pages/login` |
| 5.3 | 任务首页 | ✅ | `pages/index/index`、`tasklist` |
| 5.4.1 | 采购入库 | ✅ | `pages/inbound/receive-*`、`purchase-scan`、`direct` |
| 5.4.2 | 生产入库（成品） | 🟡 | 有生产领/退料，无独立"成品入库"PDA 页 |
| 5.4.3 | 退货入库 | ❌ | 无对应 PDA 页 |
| 5.4.4 | 委外入库 | 🟡 | `outsource-feed`（委外补料）方向相反 |
| 5.5.1 | 生产领料出库 | ✅ | `picking/production-issue*`、`issue-pick` |
| 5.5.2 | 销售出库 | 🟡 | `outbound/outbound` 扫码，无波次拣货/分拣/装车确认 |
| 5.5.3 | 退货出库 | 🟡 | `production-return`、`outsource-return`；销售退货缺 |
| 5.5.4 | 委外出库 | ✅ | `picking/outsource-issue*` |
| 5.6.1 | 库存查询 | ✅ | `pages/inventory/inventory` |
| 5.6.2 | 移库操作 | ✅ | `pages/transfer/transfer`、`MobileTransferController` |
| 5.6.3 | 库位查询 | 🟡 | `LocationPicker` 组件，无独立查询页 |
| 5.7 | 盘点管理 | ✅ | `pages/stockcheck/*`、`MobileStockcheckController` |
| 5.8 | 质检管理（来料/录入） | ❌ | 无 PDA 质检页，无移动质检接口 |
| 5.9 | 批次追溯 | ✅ | `pages/trace/trace`、`MobileTraceController` |
| 5.10 | 消息中心 | ✅ | `pages/messages/messages` |
| 5.11 | 个人中心（含扫码设置/离线缓存） | 🟡 | `profile/update/settings` 有；离线缓存为框架 |
| 5.12 | 离线模式（SQLite+同步+冲突） | 🟡 | `utils/offline.js` + `MobileSyncController`；无本地 SQLite、无冲突 UI |

---

## B. Web 后台（需求 §6）

| 编号 | 需求项 | 状态 | 证据 / 说明 |
|---|---|---|---|
| 6.2.1 | 用户管理 | ✅ | `views/system/UserList.vue` |
| 6.2.2 | 角色管理 | ✅ | `views/system/RoleList.vue`、`SysRoleController` |
| 6.2.3 | 权限管理（独立页） | 🟡 | 权限在角色内分配，无独立权限/菜单管理页 |
| 6.2.4 | 组织管理 | ❌ | 无 `sys_org` 实体/接口/页面 |
| 6.2.5 | 操作日志 | ❌ | 有 `WmsAuditTrail`+`AuditTrailService`，无查询接口/页面 |
| 6.2.6 | 系统配置 | 🟡 | `WmsBusinessRuleController` 部分替代 |
| 6.3.1 | 物料管理 | ✅ | `views/base/MaterialList.vue` |
| 6.3.2 | 物料分类 | ❌ | 无 `base_material_category` |
| 6.3.3 | 供应商管理 | 🟡 | 后端 `BaseSupplierController` 有，无 Web 页 |
| 6.3.4 | 客户管理 | ❌ | 无 `base_customer` |
| 6.3.5 | 仓库管理 | ✅ | `views/base/WarehouseList.vue` |
| 6.3.6 | 库区管理 | 🟡 | 仅 `ZoneCreateRequest` DTO，无独立实体/页面 |
| 6.3.7 | 库位管理 | ✅ | `views/base/LocationList.vue` |
| 6.3.8 | 计量单位/换算 | ❌ | 无 `base_unit`/`base_unit_convert` |
| 6.3.9 | 字典管理 | ❌ | 无 `sys_dict`/`sys_dict_item` |
| 6.4.1 | 采购入库单 | ✅ | `views/inbound/InboundOrderList.vue` |
| 6.4.2/3/4 | 生产/退货/委外入库单 | 🟡 | 复用统一入库模型，无类型专属流程 |
| 6.4.5 | 入库查询 | ✅ | 同上 + `PdaInboundRecordList`、`ReceiveBatchList` |
| 6.5.1 | 生产领料单 | 🟡 | `outbound/OutboundOrderList.vue` **未接路由/菜单** |
| 6.5.2/3/4 | 销售/退货/委外出库单 | 🟡 | 后端 `OutboundController` 支持，Web 页未接线 |
| 6.5.5 | 出库查询 | 🟡 | 同上，视图未注册 |
| 6.6.1 | 实时库存 | ✅ | `views/inventory/InventoryList.vue` |
| 6.6.2 | 批次库存 | ❌ | 无独立页（`/inventory/summary` 部分能力） |
| 6.6.3 | 库位库存 | ❌ | 无独立页 |
| 6.6.4 | 库存流水 | 🟡 | API `/inventory/transactions` 有，无页面 |
| 6.6.5 | 移库管理 | 🟡 | `views/inventory/TransferList.vue` **未接路由/菜单** |
| 6.6.6 | 库存冻结/解冻 | 🟡 | 仅 `PUT /inventory/{id}/stock-status`，无冻结单 `inventory_freeze` |
| 6.6.7 | 安全库存设置 | 🟡 | `SafetyStock` 实体被 `InventoryService` 引用，无维护接口/页面 |
| 6.7.1 | 盘点计划 | 🟡 | `views/stocktake/PlanList.vue` **未接路由/菜单** |
| 6.7.2 | 盘点任务 | ❌ | 有 API，无 Web 页 |
| 6.7.3 | 盘点差异 | 🟡 | `StockcheckController /diffs`+审批有，无页面；无 reject |
| 6.7.4 | 盘点报告 | ❌ | 未实现 |
| 6.8 | 质检管理（标准/来料/报告/不良品） | ❌ | **无 `QualityController`**，无 Web 页，无让步 `CONCESSION` |
| 6.9.1 | 工单管理 | 🟡 | `ProductionController` / MES 工序计划，无 Web 工单页 |
| 6.9.2 | BOM 管理 | 🟡 | `views/mes/BomList.vue`（MES 域，非 WMS 生产对接） |
| 6.9.3 | 领料计划 | 🟡 | `ProductionPickingPlanService`，无 Web 页 |
| 6.9.4 | 退料管理 | ✅ | `picking/production-return`、`workshop-return`、`PickingController` |
| 6.10.1 | 条码规则 | ✅ | `views/barcode/RuleList.vue` |
| 6.10.2 | 条码生成/打印/模板 | 🟡 | `barcode` + `LabelPrintJob` + `printConfig`；模板设计器权限码存在但无页面 |
| 6.11.1 | 库存报表（汇总/明细/安全/保质期） | 🟡 | 汇总/低库存有；明细、保质期缺 |
| 6.11.2 | 出入库统计 | ✅ | `/reports/inbound|outbound/statistics` |
| 6.11.3 | 周转率分析 | ❌ | 全库无 `turnover/周转` 匹配 |
| 6.11.4 | 呆滞料分析 | ❌ | 全库无 `dead-stock/呆滞` |
| 6.11.5 | 库龄分析 | ❌ | 全库无 `stock-age/库龄` |
| 6.11.6 | 作业绩效统计 | ❌ | 全库无 `operator-performance/绩效` |
| 6.12 | 系统监控（PDA在线/接口/同步） | ❌ | 未实现 |

---

## C. 数据库核心表（需求 §8.1，共 44 张）

| 表 | 状态 | 表 | 状态 |
|---|---|---|---|
| sys_user | ✅ | base_material | ✅ |
| sys_role | ✅ | base_material_category | ❌ |
| sys_permission | ✅ | base_supplier | ✅ |
| sys_user_role | ✅ | base_customer | ❌ |
| sys_role_permission | ✅ | base_warehouse | ✅ |
| sys_org | ❌ | base_warehouse_zone | 🟡 |
| sys_operation_log | 🟡（WmsAuditTrail 替代） | base_location | ✅ |
| sys_dict | ❌ | base_unit | ❌ |
| sys_dict_item | ❌ | base_unit_convert | ❌ |
| inbound_order | ✅ | outbound_order | ✅ |
| inbound_order_detail | ✅ | outbound_order_detail | ✅ |
| inventory | ✅ | inventory_freeze | ❌ |
| inventory_transaction | ✅ | safety_stock | ✅ |
| transfer_order | ✅ | stockcheck_plan | ✅ |
| stockcheck_task | ✅ | stockcheck_detail | ✅ |
| stockcheck_diff | ✅ | qc_standard | ✅ |
| qc_order | ✅ | qc_order_detail | ❌ |
| production_order | ✅ | bom_header | ✅ |
| bom_detail | ✅ | picking_plan | ✅ |
| barcode_rule | ✅ | barcode_record | ✅ |
| print_template | 🟡（LabelPrint 替代） | message | ✅ |
| pda_task | 🟡（notice bill/tasklist 替代） | pda_offline_data | ❌ |

---

## D. 非功能需求（需求 §9）

| 项 | 状态 | 说明 |
|---|---|---|
| JWT + 刷新 | ✅ | `auth/security` |
| RBAC 三级权限 | 🟡 | 菜单/操作有，数据范围 `DataScopeHelper` 部分 |
| BCrypt 密码 | ✅ | `AuthPasswordTest` |
| HTTPS / 限流 / 字段级加密 / 备份恢复 | ❌ | 未在代码中体现 |
| 事务一致性 | ✅ | 库存变更 `@Transactional` |
| 离线容错 | 🟡 | 仅框架 |
| API 版本化 `/api/v1` | ✅ | 见 README |
| 微服务就绪 / 多租户 | 🟡 / ❌ | 分包清晰但无租户字段 |
| ERP 对接 | ⭐ | 金蝶深度集成，超出文档 |

---

## E. 超出需求文档的已建能力（⭐）

- 金蝶云 ERP 双向集成：采购/生产/委外/销售/退货单据、主数据、库存回写（`integration/kingdee/`）
- 收料通知/送货单/采购订单/采购退货（`incoming/`、`pdareceive/`）
- 备料通知、拣配发料、领料执行、车间退库（`picking/`）
- 轻 MES：物料/BOM/工序/设备/工艺路线/工序计划/报工/转移/返工/同步中心（`com.wms.mes` + `views/mes/`）
- 收料入库批次、PDA 单据锁、标签打印任务、期初库存

---

## F. 建议完善顺序

1. **接线层（最快）**：注册 `OutboundOrderList` / `TransferList` / `OtherInboundList` / `OtherOutboundList` / `stocktake/PlanList` 到 `router/index.ts` + `MainLayout.vue` 菜单 + `PermissionCatalog`。
2. **补页面层**：库存流水、批次库存、库位库存、盘点任务/差异/报告、供应商维护。
3. **补模块层**：`QualityController` + Web/PDA 质检 + 让步接收；分析报表（周转/呆滞/库龄/保质期/绩效）。
4. **补主数据层**：物料分类、客户、计量单位换算、字典、组织、操作日志页面。
5. **增强层**：库存冻结单、安全库存维护、系统监控、离线 SQLite、非功能项。

---

**文档结束**
