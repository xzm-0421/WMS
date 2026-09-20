# 金蝶 AI 苍穹 / 云星空 OpenAPI 接口总结

> 生产工程数据四类核心对象：物料、BOM、工艺路线、工序计划。

## 一、通用调用模型

金蝶 OpenAPI 的增删改查走同一套标准接口，靠 **FormId（业务对象表单标识）** 区分业务对象。

| 操作 | 接口名 | 方法 | 用途 |
|------|--------|------|------|
| 列表查询 | `executeBillQuery` | POST | 按条件分页批量查询 |
| 详情查询 | `view` | POST | 按单据编号 / 内码查单体 |
| 新增 / 保存 | `batchSave`（或 `save`） | POST | 批量保存 / 新增 |
| 提交 | `submit` | POST | 提交单据 |
| 审核 | `audit` | POST | 审核单据 |

查询类请求核心参数（以物料为例）：

```json
{
  "FormId": "BD_MATERIAL",
  "FieldKeys": "FMasterId,FNumber,FName,FSpecification",
  "FilterString": "FUseOrgId.FNumber='100' and FForbidStatus='A'",
  "Limit": "2000",
  "StartRow": "0"
}
```

## 二、四类对象对照表

| 业务对象 | FormId（表单标识） | 备注 |
|---------|-------------------|------|
| 物料（基础资料） | `BD_MATERIAL` | 全领域通用主数据 |
| 物料清单 BOM | `ENG_BOM` / `PRD_BOM` | 工程数据-物料清单；生产用料展开用 `PRD_PPBOM`，树形维护用 `ENG_BOMTREE` |
| 工艺路线 | `ENG_ROUTE` | 工程数据-工艺路线 |
| 工序计划单 | `PRD_PROCESSCHEDULE` | 车间工序排程（**具体 FormId 需在 API 文档按「工序计划」确认**） |

> ⚠️ 表单标识随版本（云星空 9.0 / 苍穹 V8.x）和补丁可能有差异，以上是通用标识，落地前建议在 `openapi.open.kingdee.com` →「API 文档」里搜中文名二次核对。

## 三、各对象关键字段

### 1. 物料（BD_MATERIAL）

- 主键 `FMasterId`、编码 `FNumber`、名称 `FName`、规格型号 `FSpecification`、旧编码 `FOldNumber`
- 物料分组 `FMaterialGroup`、物料属性 `FErpClsID`、禁用状态 `FForbidStatus`
- 基本单位 `FBaseUnitId`、创建组织 `FCreateOrgId`、使用组织 `FUseOrgId`
- 可采购 `FIsPurchase`、可生产 `FIsProduce`、可销售 `FIsSale`、可库存 `FIsInventory`（常用于筛选）

### 2. BOM（ENG_BOM）

- 父项物料 `FMaterialID`（编码 / 名称）、版本 `FBOMID`、BOM 日期 / 生效日期
- 子项明细 `SubHeadEntity`（或 `BomEntry`）：子项物料、用量分子 / 分母、跳层标记、损耗率、位号
- 查询"某产品多层 BOM"常用递归 CTE 或多次展开（包装成本模块 BOM 展开的核心）

### 3. 工艺路线（ENG_ROUTE）

- 工艺路线编码 `FNumber`、名称 `FName`、工艺类型（物料 / 物料组 / 通用工序集）
- 生产车间、物料编码 `FMaterialID`、批量从 / 到、单位
- 工序明细：工序号、工作中心、标准工时（准备 / 加工 / 传送）、资源 / 作业

### 4. 工序计划单（PRD_PROCESSCHEDULE）

- 生产订单号 `MOBillNO`、物料、车间、数量
- 工序明细：工序、工作中心、计划开始 / 结束时间、计划数量、已汇报数量

## 四、查询接口示例（以 BOM 为例）

```json
{
  "FormId": "ENG_BOM",
  "FieldKeys": "FBillNo,FMaterialID.FNumber,FMaterialID.FName,FBOMID,SubHeadEntity.FMaterialID2.FNumber,SubHeadEntity.FNumerator,SubHeadEntity.FDenominator",
  "FilterString": "FMaterialID.FNumber='CP0001' and FDocumentStatus='C'",
  "Limit": "2000",
  "StartRow": "0"
}
```

## 五、权威文档入口

| 用途 | 地址 |
|------|------|
| 在线 API 文档（字段字典、参数、错误码、在线测试） | https://openapi.open.kingdee.com/ApiDoc |
| 云星空操作手册（工程数据类） | https://open.kingdee.com/k3cloud/help/ |
| 苍穹 kapi 规范 | https://developer.kingdee.com/article/367256698827672576 |
