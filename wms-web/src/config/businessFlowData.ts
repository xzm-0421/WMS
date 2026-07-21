/** 业务流程与状态流转文档数据（WMS Web 出入库边界） */

export interface FlowTransition {
  from?: string
  to: string
  label?: string
}

export interface FlowStep {
  order: number
  action: string
  channel: 'Web' | 'PDA' | 'Web/PDA' | '—'
  statusChange?: string
  inventory?: string
  note?: string
}

export interface FlowModule {
  id: string
  title: string
  subtitle?: string
  docCode?: string
  webMenu?: string
  pdaMenu?: string
  transitions: FlowTransition[]
  steps: FlowStep[]
}

export interface PdaFeature {
  name: string
  biz: string
  api?: string
}

export interface FlowGap {
  module: string
  desc: string
}

export const OVERVIEW_MODULES = [
  { group: '出入库', items: ['入库单 RK', '出库单 CK', 'PDA快速入库 PIR'] },
  { group: '库存作业', items: ['实时库存', '其他入/出库', '调拨 YK', '盘点 ST'] },
  { group: '质量', items: ['质检任务 QC', '库存抽检 SP'] },
]

export const AUTO_CODE_RULES = [
  { prefix: 'RK', name: '入库单', source: 'WMS 保存时自动生成' },
  { prefix: 'CK', name: '出库单', source: 'WMS 保存时自动生成' },
  { prefix: 'MAT', name: '物料', source: 'WMS 新建留空时自动生成；ERP 物料用外部编码' },
  { prefix: 'WH', name: '仓库', source: 'WMS 新建留空时自动生成' },
  { prefix: 'BC', name: '条码规则', source: 'WMS 新建留空时自动生成' },
]

export const INVENTORY_TX_TYPES = [
  'PURCHASE_IN',
  'PDA_INBOUND',
  'SALES_OUT',
  'PRODUCTION_OUT',
  'OTHER_IN',
  'OTHER_OUT',
  'TRANSFER_IN',
  'TRANSFER_OUT',
  'STOCKTAKE_GAIN',
  'STOCKTAKE_LOSS',
]

export const FLOW_MODULES: FlowModule[] = [
  {
    id: 'rk',
    title: '入库单',
    docCode: 'RK',
    webMenu: '出入库管理 → 入库单',
    pdaMenu: '入库作业 / 订单中心',
    subtitle: '类型：采购入库 / 生产入库 / 退货入库',
    transitions: [
      { from: 'DRAFT', to: 'PENDING', label: '提交' },
      { from: 'PENDING', to: 'INBOUND', label: '审核' },
      { from: 'INBOUND', to: 'COMPLETED', label: '扫码收满 / 手动完成' },
      { from: 'DRAFT', to: 'CANCELLED', label: '取消' },
      { from: 'INBOUND', to: 'PENDING', label: '反审核（无收货记录）' },
      { from: 'INBOUND', to: 'CLOSED', label: '关闭' },
    ],
    steps: [
      { order: 1, action: '新建入库单', channel: 'Web', statusChange: '→ DRAFT' },
      { order: 2, action: '提交 → 审核', channel: 'Web', statusChange: '→ PENDING → INBOUND' },
      { order: 3, action: '扫码收货', channel: 'Web/PDA', inventory: '+PURCHASE_IN 等' },
      { order: 4, action: '全部收完', channel: 'Web/PDA', statusChange: '→ COMPLETED' },
    ],
  },
  {
    id: 'pir',
    title: 'PDA 快速入库',
    docCode: 'PIR',
    webMenu: '出入库管理 → PDA入库记录',
    pdaMenu: '快速入库',
    subtitle: '独立于 RK 入库单的现场快速收货',
    transitions: [
      { from: 'SUBMITTED', to: 'AUDITED', label: 'Web 审核' },
      { from: 'SUBMITTED', to: 'REVERSED', label: '冲销' },
    ],
    steps: [
      { order: 1, action: '扫码提交', channel: 'PDA', statusChange: '→ SUBMITTED', inventory: '立即 +PDA_INBOUND' },
      { order: 2, action: '审核 / 冲销 / 重同步 ERP', channel: 'Web', note: 'ERP：PENDING → SYNCING → SUCCESS/FAILED' },
    ],
  },
  {
    id: 'ck',
    title: '出库单',
    docCode: 'CK',
    webMenu: '出入库管理 → 出库单',
    pdaMenu: '出库作业',
    subtitle: '类型：销售出库 / 领料出库 / 调拨出库',
    transitions: [
      { from: 'DRAFT', to: 'PENDING', label: '提交' },
      { from: 'PENDING', to: 'PICKING', label: '审核' },
      { from: 'PICKING', to: 'OUTBOUND', label: '首次扫码出库' },
      { from: 'OUTBOUND', to: 'COMPLETED', label: '扫满 / 完成' },
      { from: 'DRAFT', to: 'CANCELLED', label: '取消' },
    ],
    steps: [
      { order: 1, action: '新建 → 提交 → 审核', channel: 'Web', statusChange: '→ DRAFT → PENDING → PICKING' },
      { order: 2, action: '推荐库位', channel: 'Web' },
      { order: 3, action: '扫码出库', channel: 'PDA', inventory: '-SALES_OUT / -PRODUCTION_OUT 等' },
      { order: 4, action: '完成', channel: 'Web/PDA', statusChange: '→ COMPLETED' },
    ],
  },
  {
    id: 'oi-oo',
    title: '其他入库 / 其他出货',
    docCode: 'OI / OO',
    webMenu: '库存管理 → 其他入库 / 其他出货',
    transitions: [{ to: 'COMPLETED', label: '创建即完成' }],
    steps: [
      { order: 1, action: '填写并保存', channel: 'Web', statusChange: '→ COMPLETED', inventory: 'OTHER_IN / OTHER_OUT' },
    ],
  },
  {
    id: 'yk',
    title: '库存调拨',
    docCode: 'YK',
    webMenu: '库存管理 → 库存调拨',
    transitions: [
      { from: 'PENDING', to: 'EXECUTING', label: '审批' },
      { from: 'EXECUTING', to: 'COMPLETED', label: '执行' },
      { from: 'PENDING', to: 'CANCELLED', label: '取消' },
    ],
    steps: [
      { order: 1, action: '申请调拨', channel: 'Web', statusChange: '→ PENDING' },
      { order: 2, action: '审批 → 执行', channel: 'Web', statusChange: '→ EXECUTING → COMPLETED', inventory: 'TRANSFER_OUT + TRANSFER_IN' },
      { order: 3, action: 'PDA 移库', channel: 'PDA', note: '自动创建并完成 YK 调拨单（transferType=PDA）' },
    ],
  },
  {
    id: 'sp',
    title: '库存抽检',
    docCode: 'SP',
    webMenu: '库存管理 → 库存抽检',
    transitions: [{ from: 'PLANNED', to: 'IN_PROGRESS', label: '生成清单' }],
    steps: [
      { order: 1, action: '创建抽检计划', channel: 'Web', statusChange: '→ PLANNED' },
      { order: 2, action: '生成清单并执行', channel: 'Web', statusChange: '→ IN_PROGRESS' },
      { order: 3, action: '录入结果', channel: 'Web', statusChange: 'QUALIFIED / UNQUALIFIED' },
    ],
  },
  {
    id: 'qc',
    title: '质检任务',
    docCode: 'QC',
    webMenu: '库存管理 → 质检任务',
    pdaMenu: '质检作业',
    transitions: [{ from: 'PENDING', to: 'COMPLETED', label: '提交结果' }],
    steps: [
      { order: 1, action: '扫码收货', channel: 'Web/PDA', note: '有质检标准则自动建 QC' },
      { order: 2, action: '待检任务处理', channel: 'Web/PDA', statusChange: 'PENDING → COMPLETED' },
      { order: 3, action: '录入结果', channel: 'Web/PDA', note: 'PASS/FAIL 回写入库明细 qcStatus' },
    ],
  },
  {
    id: 'st',
    title: '盘点管理',
    docCode: 'ST',
    webMenu: '库存管理 → 库存盘点',
    pdaMenu: '盘点作业',
    subtitle: '计划 → 任务 → 明细 → 差异',
    transitions: [
      { from: 'DRAFT', to: 'PUBLISHED', label: '发布计划' },
      { from: 'PENDING', to: 'COUNTING', label: 'PDA 扫码盘点' },
      { from: 'COUNTING', to: 'COMPLETED', label: '完成盘点' },
      { from: 'PENDING', to: 'APPROVED', label: '差异审批' },
    ],
    steps: [
      { order: 1, action: '新建盘点计划', channel: 'Web', statusChange: '计划 DRAFT → PUBLISHED' },
      { order: 2, action: '生成任务', channel: 'Web', statusChange: '任务 PENDING' },
      { order: 3, action: '扫码盘点 / 盘盈 / 空库位确认', channel: 'PDA', statusChange: '→ COUNTING，明细 COUNTED' },
      { order: 4, action: '完成盘点', channel: 'PDA', statusChange: '任务 → COMPLETED' },
      { order: 5, action: '差异审批', channel: 'Web', statusChange: '差异 PENDING → APPROVED', inventory: 'STOCKTAKE_GAIN / STOCKTAKE_LOSS 调库存' },
    ],
  },
]

export const FLOW_GAPS: FlowGap[] = []

export const PDA_FEATURES: PdaFeature[] = [
  { name: '入库作业', biz: '入库单 RK', api: '/mobile/inbound/*' },
  { name: '快速入库', biz: 'PDA入库 PIR', api: 'POST /mobile/pda-inbound/submit' },
  { name: '出库作业', biz: '出库单 CK', api: '/mobile/outbound/*' },
  { name: '移库', biz: '即时移库（非 YK 单）', api: 'POST /mobile/transfer' },
  { name: '盘点', biz: '盘点任务', api: '/mobile/stockcheck/*' },
  { name: '质检', biz: '质检任务 QC', api: '/mobile/quality/*' },
  { name: '库存查询', biz: '实时库存', api: '/mobile/inventory/query' },
  { name: '批次追溯', biz: '流水追溯', api: '/mobile/trace' },
]
