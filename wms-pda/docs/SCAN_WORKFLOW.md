# WMS PDA 统一扫码方案

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────┐
│  ScanWorkbench（可复用工作台）                            │
│  ├── ScanInput（自动聚焦 + 回车提交 + 手动输入 + 摄像头）   │
│  ├── useScanFlow（入库/出库分支逻辑）                      │
│  └── 扫码记录 / 出库确认面板                               │
└─────────────────────────────────────────────────────────┘
         │ inbound                    │ outbound
         ▼                            ▼
  POST /mobile/scan/inbound/{no}   POST /mobile/scan/outbound/{no}
         │                            ├── confirm=false → 预览
         │                            └── confirm=true  → 扣减
         ▼                            ▼
  InboundService.scanReceive     OutboundService.scanIssue
```

## 2. 接口定义

### 2.1 条码识别

`POST /api/v1/mobile/scan/recognize`

**请求**
```json
{
  "barcodeContent": "MAT00000001B202606250001",
  "warehouseCode": "WH01"
}
```

**响应**
```json
{
  "code": 200,
  "data": {
    "barcodeContent": "...",
    "materialCode": "MAT00000001",
    "batchNo": "B202606250001",
    "materialName": "物料名称",
    "specification": "规格",
    "unitCode": "PCS",
    "materialExists": true
  }
}
```

### 2.2 库存匹配（出库预览用）

`POST /api/v1/mobile/scan/match-inventory`

**请求** 同 recognize

**响应**
```json
{
  "code": 200,
  "data": {
    "matched": true,
    "materialCode": "MAT00000001",
    "materialName": "物料名称",
    "specification": "规格",
    "totalAvailableQty": 100,
    "recommendedLocation": "WH01A1010101",
    "recommendedBatchNo": "B001",
    "recommendedAvailableQty": 50,
    "stocks": [
      { "locationCode": "...", "batchNo": "...", "availableQty": 50 }
    ]
  }
}
```

### 2.3 入库扫码登记

`POST /api/v1/mobile/scan/inbound/{orderNo}`

**请求**
```json
{
  "barcodeContent": "MAT00000001B202606250001",
  "quantity": 1,
  "targetLocation": "WH01A1010101",
  "lineNo": null,
  "deviceNo": "PDA-SN-DEV001"
}
```

**响应**
```json
{
  "code": 200,
  "message": "入库成功",
  "data": {
    "orderNo": "RK...",
    "lineNo": 1,
    "receivedQty": 5,
    "orderQty": 10,
    "lineStatus": "PARTIAL",
    "orderStatus": "INBOUND",
    "allCompleted": false,
    "materialName": "...",
    "scannedQty": 1,
    "mode": "INBOUND"
  }
}
```

### 2.4 出库扫码（预览 / 确认）

`POST /api/v1/mobile/scan/outbound/{orderNo}`

**预览请求** `confirm: false`
```json
{
  "barcodeContent": "MAT00000001B202606250001",
  "quantity": 1,
  "confirm": false,
  "deviceNo": "PDA-SN-DEV001"
}
```

**预览响应**
```json
{
  "code": 200,
  "data": {
    "preview": true,
    "materialName": "物料名称",
    "specification": "规格",
    "sourceLocation": "WH01A1010101",
    "quantity": 1,
    "totalAvailableQty": 100,
    "recommendedAvailableQty": 50,
    "message": "请确认后扣减库存"
  }
}
```

**确认请求** `confirm: true`（需带 preview 返回的 lineNo、sourceLocation 等）

## 3. 关键流程

### 3.1 入库连续扫码

```
扫描条码 → recognize(可选) → scan/inbound
  → 自动匹配未完成明细行（按 materialCode）
  → 默认 qty=1，不超过行剩余量
  → 登记库存 + 更新明细
  → 清空输入框 + 重新聚焦 → 继续下一扫
```

### 3.2 出库扫码确认

```
扫描条码 → scan/outbound(confirm=false)
  → 匹配明细行 + FIFO 库存
  → 展示确认面板（名称/规格/库存）
  → 用户确认 → scan/outbound(confirm=true)
  → 扣减库存 + 更新明细
```

### 3.3 异常与边界

| 场景 | 错误码 | 处理 |
|------|--------|------|
| 条码为空 | 400 | 提示重新扫描 |
| 无法识别物料 | 400 | 手动输入条码 |
| 物料不在单据明细 | CONFLICT / LINE_NOT_FOUND | Toast 提示 |
| 明细行已完成 | LINE_COMPLETED | 跳过或提示 |
| 数量超出订单 | QTY_EXCEED_ORDER | 自动截断至剩余量 |
| 无可用库存（出库） | NO_STOCK | 提示检查库存 |
| 网络失败 | - | 离线队列缓存（入库/出库） |
| 单据已结束 | ORDER_STATUS_CONFLICT | 禁止操作 |

## 4. 前端组件

| 文件 | 职责 |
|------|------|
| `components/ScanInput.vue` | 聚焦输入、回车提交、手动模式、摄像头 |
| `components/ScanWorkbench.vue` | 统一工作台 UI |
| `composables/useScanFlow.js` | 入库/出库业务分支 |
| `api/scan.js` | 统一扫码 API 封装 |

## 5. 条码格式约定

| 格式 | 示例 | 解析 |
|------|------|------|
| 固定长度 | `MAT00000001B202606250001` | 前11位物料 + 后续批次 |
| 分隔符 | `MAT00000001\|B001` | 物料 \| 批次 |
| 库位码 | `WH01A1010101` | 库位（出库源库位） |
