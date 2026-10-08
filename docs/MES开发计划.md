# YM_MES 开发计划（内码改造 · 功能补全 · 详情页统一改造）

> 版本：v1.0
> 范围：轻MES + WMS（内码改造含 WMS；功能补全与详情页仅 MES）
> 说明：本文由《物料内码改造开发计划》升级合并而来，包含三大块：**物料内码改造**、**功能补全**、**详情页统一改造**。
> 相关细化文档：`MES功能补全开发方案.md`（阶段 B 细化）

---

## 目录

1. 现状总览
2. 物料内码改造（P1 / P2 / P3）
3. 功能补全（阶段 A / B / C + 批次二）
4. 详情页统一改造
5. 里程碑与分工
6. 验收与风险

---

# 1. 现状总览

| 模块 | 状态 |
|---|---|
| 轻MES 主体闭环（基础资料/工序计划/报工/转移/返工/同步） | ✅ 已完成 |
| 阶段 A（转移记录、同步中心聚合、移动端首页、返工状态机） | ✅ 已完成 |
| 批次二（不良单 / 返工工单回传 ERP，出站表） | ✅ 已实现（金蝶 FormId 占位待联调） |
| 平板现场作业 + 标签（称重/打印初版） | 🟡 组员初版（`32fe3cc`），存在缺口 |
| 详情页统一改造 | ✅ 已完成 |
| 物料内码改造 | 🟡 P1/P2 已完成，P3（WMS）未开始 |
| 阶段 C（离线 / 安全 / 埋点 / 告警） | 🟡 C2.4/C2.5/C3.1/C4.1 已完成，其余未开始 |
| 商用许可（阶段 D） | ❌ 未开始 |

**已确认决策（内码）**：内码与编码并存、内码列不可为空、关联优先内码；ERP 回写保持编码；`inventory` 唯一键由内码构成；分批 P1→P2→P3；弃用 `mes:material:*`。

---

# 2. 物料内码改造

> 目标：物料同步引入金蝶内码，全系统涉及物料的功能携带内码，关联优先内码。
> 内码来源：主数据 `BD_MATERIAL` 的 `FMATERIALID`；单据行取 `FMaterialId`。

## 2.0 全局约定
- 列名统一 `erp_material_id BIGINT`（物料/产品一致）；保留 `material_code`/`product_code`。
- 关联/匹配：优先 `erp_material_id`，为空回退编码（回填后收紧）。
- ERP 写回：不改（编码）。
- Flyway 双库（sqlserver + h2），每次同步两套；迁移三步：`加可空列 → 回填 → NOT NULL + 索引`。
- ⚠️ 内码 NOT NULL 前提：**所有物料必须来自金蝶并带内码**；存量必须先回填。

## 2.1 影响面总览
**表（约 30）**：base_material；inbound_order_detail；outbound_order_detail；inventory；inventory_transaction；safety_stock；stockcheck_detail；stockcheck_diff；qc_standard；qc_order；barcode_instance；barcode_serial_registry；barcode_trace_link；barcode_archive；label_print_job；production_order；bom_header；bom_detail；mes_route；mes_route_op；mes_op_plan；pda_receive_scan_line；pda_inbound_record；pda_stockcount_line；prep_notice_line；pick_issue_line；workshop_return_line；purchase_order_line；delivery_note_line；purchase_return_line；transfer_order；other_inbound_line；other_outbound_line；sample_result。

**金蝶字段键**：materialSyncFieldKeys、bomList/Detail、mesRoute/Alt、mesOpPlan/Alt、prdMorpt/InStock/RetStock、feedMtrl、ppBom、pickMtrl、returnMtrl、sub*、batch/serial、stockCount、receiveBill、misc、salesDelivery、salReturn*、purMrb 等——物料字段成对加入「内码」。

**固定列索引解析器（高风险）**：
- `KingdeeReceiveBillDetailRowParser`（`IDX_MATERIAL_CODE`）
- `KingdeeStockCountService`（`cell(row,5/6)`）
- 加列会错位，必须同步改列索引。

**前端**：`api/material.ts`、`api/mes.ts` 及所有携带 materialCode/productCode 的 API 类型；`MaterialPickerDialog.vue`、`MaterialSelectInput.vue`、`useMaterialPicker.ts`、`applyMaterialBasic`；各业务页；`wms-pda` 全部物料页与 composable。

## 2.2 阶段 P1 — 基础数据（物料）
**目标**：物料内码贯通主数据、选择器、通用物料页；MES 物料页合并。

- P1.1 DB：`base_material` 加 `erp_material_id`（可空→回填→NOT NULL + 唯一索引 `uk_material_erp_id`）。
- P1.2 后端：`BaseMaterial` 加 `erpMaterialId`；`materialSyncFieldKeys` 加 `FMATERIALID`；`syncMaterials`/`upsertFromKingdee` 内码优先/编码兜底；`create/update` 是否禁本地（见 6.待决）；物料 DTO/VO 带内码。
- P1.3 前端：`Material` 加 `erpMaterialId`；`applyMaterialBasic` 回填内码；选择器加「内码」列并透出；验证 7 处消费者（入库/出库/其他入出库/抽检/移库/标签）。
- P1.4 合并物料页：删 `views/mes/MaterialList.vue`；移除路由 `/mes/materials`；`/mes/master` → `/base/materials`；轻MES 菜单去物料；`permission.ts`/`canAccessPath` 调整；`PermissionCatalog` 删 `MES_MATERIAL_LIST/SYNC`、增 `BASE_MATERIAL_SYNC`；清理角色残留。

**验收**：同步后 `erp_material_id` 全非空；按编码/内码可查；选择器带内码；MES 无物料菜单、基础数据物料页完整；构建通过。

## 2.3 阶段 P2 — MES（工序 / 工艺路线 / 工序计划 / BOM）
- P2.1 DB：`bom_header/bom_detail`、`mes_route`、`mes_route_op`、`mes_op_plan` 加 `erp_material_id`（回填→NOT NULL）。
- P2.2 金蝶/同步：字段键加内码（bomList/Detail、mesRoute/Alt、mesOpPlan/Alt）；`KingdeeBomService`、`MesKingdeePullService` 写内码；`MesKingdeeRow` 解析。工序基础资料保持编码。
- P2.3 业务/接口：MES 主数据查询/详情返回并按内码关联，VO 带内码。
- P2.4 前端：`api/mes.ts` 相应类型加内码；列表编码/工单号 → 详情页（见第 4 章）。

**验收**：BOM/路线/计划产品与子件内码非空；详情可打开；构建通过。

## 2.4 阶段 P3 — WMS（逐模块）
- P3.1 DB：各表加 `erp_material_id`（回填→NOT NULL）；`inventory` 唯一键改为 `uk_inventory(warehouse_code, location_code, erp_material_id, batch_no)`（回填后重建，先核对重复）。
- P3.2 金蝶同步写内码：ReceiveBill、PrdInStock/RetStock、FeedMtrl、PickMtrl/ReturnMtrl、Sub*、Misc、MisDelivery、SalOutStock、SalReturn*、PurMrb、StockCount、Batch/Serial 等解析器与固定列索引调整。
- P3.3 业务逻辑（优先内码，编码回退）：库存 `InventoryService`/`InventoryMapper`/`InventoryQueryMapper`/`ReportMapper`/`LocationAllocationService`；FIFO `OutboundService`/`MobileScanService`；拣配 `PickIssueService`/`PrepNoticeService`/`ProductionPickingPlanService`；收货 `PdaReceiveScanService`/`PdaReceiveSubmitBatchService`；盘点质检 `StockcheckService`/`PdaStockCountService`/`QualityService`；条码打印 `Barcode*Service`/`MobilePrintService`。
- P3.4 前端/终端：wms-web 各 API 类型与页面补内码；wms-pda 物料页/scan composable/`LocationPicker`/提交 payload 补内码。

**验收**：库存按内码归并且唯一；FIFO/拣配/收货/盘点/条码按内码匹配；无内码为空；逐模块回归通过。

---

# 3. 功能补全

> 详见 `MES功能补全开发方案.md`（阶段 B 细化）。

## 3.1 阶段 A（已完成）
- A1 工序转移记录 + 重试；A2 同步中心聚合（报工/转移/不良/返工分类型）；A3 移动端首页统计 `/mobile/mes/summary`；A4 返工状态机（全手动 complete/secondary/close）+ 序列图谱；A5 单测与 README。均已交付，后端测试 93/93。

## 3.2 批次二（已实现，待联调）
- 出站表 `mes_erp_outbox`（Flyway `V1_0_48`）承载不良单 + 返工工单的 `CREATE`/`SUBMIT`。
- `MesDefectSaveBuilder` / `MesReworkOrderSaveBuilder`；`KingdeeCloudProperties` 加 FormId/字段（占位）。
- `MesSyncWorkerService.processOutbox()`：CREATE→`rawSave`，SUBMIT→`submitAndAuditExistingBill`。
- 触发：创建不良单入队 CREATE；「完成返工」入队 SUBMIT；`/mes/sync/panel` 计数；`/mes/sync/outbox` 列表与重试。
- **待办**：金蝶实施方提供不良单/返工工单 **FormId + 字段映射**并联调。

## 3.3 阶段 B — 平板现场作业 + 框料标签
> 初版由组员完成（提交 `32fe3cc`「完善平板端的称重打印功能」）。
**组员已完成**：`station.vue` 工单列表（`GET /mes/plans`）→ 手动输入重量 → `POST /mobile/mes/reports` → 打印队列 → `POST /mobile/print/label/resolve`；新增 `MES-tablet/src/api/mes.js`；模拟数据回退。
**待补全/修复**：
- 报工类型 `WEIGH` → `NORMAL`；数量去掉 `weight*4`；设备/工序去掉硬编码，改用 `/mobile/mes/reports/context`。
- 检索参数改用 `moNo/productCode/status`；生产环境禁用模拟数据。
- 新增 `utils/scale.js` + `config.js` 的 `scale` 配置（手动输入，预留秤适配）。
- MES 框料标签：替换不匹配的 WMS `/mobile/print/label/resolve`，新建 `com.wms.mes.label`（`/mobile/mes/labels`）+ 专用模板；平板按框 + 蓝牙打印；Flyway `V1_0_49__mes_label.sql`。
- 称重工序标识（`mes_process`）。
**联调**：平板 + 电子秤（手动→自动）+ 蓝牙打印机 + 金蝶。

## 3.4 阶段 C — 可靠性与安全非功能
- 客户端离线 SQLite + 冲突处理；ERP IP 白名单；API 签名；敏感字段 AES-256；密码强度 + 90 天过期；操作日志查询页；埋点 §5.2；告警通道（队列满 / SLA / 长期未同步）；HTTPS/TLS、每日备份、车间数据隔离。

## 3.5 阶段 D — 商用许可（License）

> 目标：面向商用，仅“已授权”主体可使用；未授权 = **只读模式**（可登录/查询/改密，拦截全部写操作）。

### 已定参数
- **双许可**：部署许可（License）+ 账号授权。
- **许可码在线激活**：在产品内输入许可码激活部署许可。
- **签名算法**：Ed25519/RSA 非对称（厂商私钥签发、系统内置公钥验签）。
- **不绑定机器码**：仅校验签名 + 产品/版本 + 有效期 + 最大用户数；机器码仅展示。
- **只读拦截**：未授权账号（或部署许可无效）拦截全部写操作（POST/PUT/DELETE）。
- **豁免**：SUPER_ADMIN 恒豁免只读，避免锁死。
- **存量处理**：迁移后仅 `SUPER_ADMIN` 角色用户默认授权，其余需管理员开通。

### 有效性判定
`effectiveLicensed = 部署许可 ACTIVE(签名有效、未过期、未吊销) AND 账号 licensed=1(且未过期)`；未有效 → 只读。

### 任务分解
- **D0 迁移 `V1_0_55`（h2 + sqlserver）**：`sys_user` 加 `licensed`、`license_expire_at`；存量仅 SUPER_ADMIN 置 1；新表 `sys_license`（license_code/product/edition/modules/max_users/issued_at/expire_at/activated_at/activated_machine/signature/status）。
- **D1 许可码编解码**：`LicenseCodec`（载荷 `base64url(payload).base64url(Ed25519签名)`，内置公钥验签）+ 厂商签发工具 + `LicenseCodecTest`。
- **D2 许可服务**：`SysLicense` 实体/Mapper + `LicenseService`（`current/activate/isDeploymentValid/revoke`，带缓存与定时刷新）；登录/`userinfo` 返回 `licensed/readonly/deploymentLicensed/licenseExpireAt/machineCode`。
- **D3 只读拦截**：`LicenseReadonlyFilter`（非 GET 且非白名单 → `403 LICENSE_READONLY`，SUPER_ADMIN 豁免）；白名单 `auth/**`、`system/license/activate`、Public 路径；前端只读 banner + `request` 统一处理 + 写按钮禁用。
- **D4 前端**：`views/system/LicenseManage.vue`（机器码/状态/到期/激活/吊销）；`UserList.vue` 授权开关与到期日；权限码 `system:license:list`、`system:license:manage`。
- **D5 配置**：`wms.license.enabled`（dev 默认 false=全部视为已授权；prod true）、`public-key`、`grace-days`、期望 `product/edition`。
- **D6 验收**：未激活→只读；激活→可用；过期→只读；账号未授权→该账号只读；超管始终可用；`enabled=false`→全部可用；`mvn -o test`、`vue-tsc` 通过。

---

# 4. 详情页统一改造（全屏路由，仅 MES）

> 参照金蝶工序计划详情页：顶部「基本信息」标签-值栅格（必填红 `*`）+ 下方页签区（左树右表）。
> 决策：**隐藏金蝶专属页签**（单位换算/派工/活动/委外/检验/作业指导书），**仅保留 MES 相关子集**；左树简化为**单个「主干序列」组**；缺失字段随「内码 + 明细」同步补齐。

## 4.1 路由与列表入口
| 页面 | 路由 | 入口 |
|---|---|---|
| 工序计划详情 | `/mes/plans/:id` | 工序计划列表点击工单号/编码 |
| 工艺路线详情 | `/mes/routes/:id` | 工艺路线列表点击编码 |
| 工序详情 | `/mes/process/:id` | 工序列表点击编码 |
| 设备详情 | `/mes/equipment/:id` | 设备列表点击编码 |
| 报工记录详情 | `/mes/reports/:reportNo` | 报工记录列表点击单号 |
| 工序转移详情 | `/mes/transfers/:transferNo` | 转移记录列表点击单号 |
| 不良/返工详情 | `/mes/rework/:defectNo` | 不良返工列表点击单号 |

## 4.2 共享组件（`wms-web/src/components/detail/`）
- `DetailShell.vue`：标题栏（编码/工单号 + 返回 + 状态标签）+ 基本信息区 + 页签区。
- `BasicInfoGrid.vue`：标签-值栅格（默认 4 列，可 6/8），必填红 `*`，只读。
- `DetailTabs.vue`：按子集渲染页签，无数据不显示。
- `SequenceOpsPanel.vue`：左「主干序列」单组树（含搜索/工具条）+ 右明细表（可配列）。

## 4.3 各页内容
- **工序计划**：基本信息（见字段映射）+ 页签「工序明细」（左树右表）/「报工记录」。
- **工艺路线**：基本信息 + 页签「工序明细」。
- **工序 / 设备**：仅基本信息。
- **报工记录**：基本信息 + 「报工明细」（同工单·同工序报工列表）+ 「同步信息」（状态/重试次数/ERP单号/失败原因）。
- **工序转移**：基本信息 + 关联信息。
- **不良/返工**：基本信息 + 「返工序列」（左树右表）。

## 4.4 工序计划 · 基本信息字段映射（缺失项随同步补齐）
单据编号 `erpBillNo` / 单据类型=工序计划 / 单据状态 `planStatus·erpStatus` / 工单号 `moNo` / 产品编码·名称(+内码) `productCode·productName` / 数量 `planQty` / 已报工 `reportedQty` / 生产车间 `workShopName` / 工艺路线（内码关联）/ 计划起止 `planStart·planEnd` / 超收比例 / 同步状态·时间 / 变更拒绝原因。

## 4.5 工序明细表列
`# / 工序序列(seqNo) / 工序号 / 工序编码 / 工序名称 / 工序说明 / 工序状态 / 工序单位 / 工序数量 / 加工组织 / 工作中心`（只读，取自 `MesRouteOp`）。

---

# 5. 里程碑与分工

## 5.1 里程碑
| 阶段 | 内容 | 依赖 |
|---|---|---|
| P1 | 物料内码 + 合并物料页 | 无 |
| P2 | MES 内码（工序/路线/计划/BOM） | P1 |
| 详情页 | 全屏详情页统一改造 | P2（字段） |
| P3 | WMS 内码逐模块 | P1 |
| 阶段 B | 平板修复 + MES 框料标签 | 硬件/金蝶联调 |
| 阶段 C | 离线/安全/埋点/告警 | — |
| 阶段 D | 商用许可（双许可 / 许可码激活 / 未授权只读） | — |

## 5.2 分工建议（2 人 + 实施）
| 角色 | 负责 |
|---|---|
| A（后端） | P1.1、P1.2、P2.1~P2.3、P3.1~P3.3（表结构/迁移回填/金蝶字段键与解析/同步写内码/库存·FIFO·拣配·收货·盘点）、阶段B 标签后端、批次二联调、阶段C 后端安全 |
| B（前端） | P1.3、P1.4、P2.4、详情页第 4 章、P3.4、阶段B 平板修复与打印、阶段C 离线与埋点 |
| 实施 | Flyway 执行、角色权限清理、现场联调 |

> 2 人制：后端内码链 **P1→P2→P3 为关键路径**；前端详情页依赖 P2 字段（W2 先做组件、W3 起做页面）。

## 5.3 人日与负荷（2 名开发）


| 轨道 | 任务 | 人日 |
|---|---|---|
| 后端 | P1.1 迁移                  | 1 |
| 后端 | P1.2 物料内码              | 2 |
| 后端 | P2.1 MES/BOM 列            | 1.5 |
| 后端 | P2.2 字段键/同步           | 2.5 |
| 后端 | P2.3 查询/VO               | 1 |
| 后端 | P3.1 列 + inventory 唯一键 | 2 |
| 后端 | P3.2 同步写内码            | 4 |
| 后端 | P3.3 WMS 业务              | 6 |
| 后端 | 阶段B 标签后端              | 3 |
| 后端 | 批次二联调                 | 1.5 |
| 后端 | 阶段C 后端安全             | 4 |
| 后端 | 回归支撑                   | 4 |
| **后端小计** | | **~32.5** |
| 前端 | P1.3 选择器/类型            | 2 |
| 前端 | P1.4 合并物料页            | 1.5 |
| 前端 | 详情页共享组件             | 3 |
| 前端 | 详情页 ×7 + 入口           | 8 |
| 前端 | P3.4 终端补内码            | 3 |
| 前端 | 阶段B 平板修复             | 2.5 |
| 前端 | 阶段B 打印（按框/蓝牙）    | 2 |
| 前端 | 阶段C 离线 + 埋点          | 4 |

| **前端小计** | | **~28** |
| **合计** | | **~60.5** |

| 角色 | 容量 | 工作量 | 利用率 | 松弛 |
|---|---|---|---|---|
| 后端 A | 40 | ~32.5 | 81% | ~7.5 |
| 前端 B | 37 | ~28 | 70% | ~12 |
| 合计 | 77 | ~60.5 | 76% | ~19.5 |



---

# 6. 验收与风险

## 6.1 待决（影响 P1）
1. **本地新增物料**：内码 NOT NULL 下是否禁用物料页「新增/编辑/删除」（只读+同步）？否则需本地生成内码（负号序列）。
2. **非 ERP 存量物料**：`removeStaleLocalMaterials` 会删除非 ERP 物料——确认保留。
3. **回填来源**：内码靠重新同步回填；未同步单据行是否先为空、随后补（NOT NULL 前必须全回填）。
4. **BOM 子件**：子件内码取自 `FTreeEntity_FMaterialIdChild`，确认字段键。
5. **加列节奏**：P1 一次预留 P2/P3 列（分批启用）还是严格分批加列。

## 6.2 统一验证清单
- 每条迁移：sqlserver + h2 双份、含回滚说明。
- 内码非空校验：各表 `erp_material_id IS NULL` 计数为 0。
- 关联正确性：按内码 join 与按编码结果一致。
- 回归：物料选择器 7 处、库存键、FIFO、拣配、收货、盘点、条码。
- 构建：后端 `mvn compile` + `mvn test`；前端 `vue-tsc`。

## 6.3 风险
- 金蝶固定列索引解析器因加列错位（receiveBill / stockCount）。
- 内码 NOT NULL 与本地物料/存量数据冲突（见 6.1）。
- 金蝶 Save 对 `FMaterialId` 取值格式（本项目回写保持编码，规避）。
- 平板端现有占位逻辑与模拟数据需在生产前清理。

---

*文档结束*
