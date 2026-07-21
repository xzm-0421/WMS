/* ===== 套打设计器 - 核心类型定义 ===== */

/** 元素类型枚举 */
export type ElementType =
  | 'text'        // 静态文本
  | 'data-field'  // 数据字段（绑定字段动态取值）
  | 'table'       // 表格
  | 'image'       // 图片
  | 'barcode'     // 条形码/二维码
  | 'line'        // 线条
  | 'rectangle'   // 矩形
  | 'page-number' // 页码

/** 元素分组（工具箱分类） */
export type ElementCategory = 'basic' | 'data' | 'drawing' | 'system'

/** 工具箱元素模板 */
export interface ToolboxItem {
  type: ElementType
  label: string
  category: ElementCategory
  icon: string
  defaultWidth: number
  defaultHeight: number
}

/** 画布上的元素实例 */
export interface CanvasElement {
  id: string
  type: ElementType
  x: number; y: number; width: number; height: number
  zIndex: number
  locked: boolean

  // 文本类属性
  text?: string
  fontSize?: number
  fontFamily?: string
  fontWeight?: 'normal' | 'bold'
  fontStyle?: 'normal' | 'italic'
  textAlign?: 'left' | 'center' | 'right'
  color?: string
  backgroundColor?: string
  borderWidth?: number
  borderColor?: string
  borderStyle?: 'solid' | 'dashed' | 'dotted' | 'none'
  padding?: number

  // 数据字段属性
  fieldBinding?: FieldBinding
  format?: string

  // 图片属性
  imageUrl?: string
  imageFit?: 'contain' | 'cover' | 'fill' | 'none'

  // 条形码属性
  barcodeType?: 'code128' | 'code39' | 'qr' | 'ean13'
  barcodeValue?: string
  barcodeWidth?: number

  // 线条属性
  lineWidth?: number
  lineColor?: string

  // 表格属性（旧版兼容）
  tableColumns?: TableColumn[]
  tableRows?: number

  // 数据表格（新版）
  tableGrid?: TableGrid

  // 页码属性
  pageNumberFormat?: string
}

// ============================================================
// 数据表格类型
// ============================================================

/** 表格单元格类型 */
export type GridCellType = 'text' | 'field' | 'statistic' | 'dynamic'

/** 格式分类 */
export type FormatCategory = 'general' | 'number' | 'currency' | 'accounting' | 'date' | 'time' | 'percentage' | 'fraction' | 'scientific' | 'text' | 'special' | 'custom'

/** 单元格边框 */
export interface CellBorder {
  color?: string       // 默认 #000
  style?: 'solid' | 'dashed' | 'dotted' | 'none'
  width?: number       // 0-10
}

/** 单元格样式 */
export interface CellStyle {
  fontFamily?: string
  fontSize?: number
  fontWeight?: 'normal' | 'bold'
  fontStyle?: 'normal' | 'italic'
  color?: string
  backgroundColor?: string
  horizontalAlign?: 'left' | 'center' | 'right'
  verticalAlign?: 'top' | 'middle' | 'bottom'
  wordWrap?: boolean
  autoShrink?: boolean
  lineHeight?: number
  letterSpacing?: number
  fillGrid?: boolean   // 填格
  boldSize?: number    // 加粗大小
  showLeadingZero?: boolean
  // 四条边独立边框
  borderTop?: CellBorder
  borderBottom?: CellBorder
  borderLeft?: CellBorder
  borderRight?: CellBorder
}

/** 表格单元格 */
export interface GridCell {
  type: GridCellType
  /** 静态文本 */
  text?: string
  /** 字段绑定 */
  binding?: FieldBinding
  /** 格式化字符串 */
  format?: string
  /** 格式分类 */
  formatCategory?: FormatCategory
  /** 样式覆盖 */
  style?: CellStyle
  /** 前缀 */
  prefix?: string
  /** 后缀 */
  suffix?: string
  /** 值为空时不显示前后缀 */
  hidePrefixSuffixWhenEmpty?: boolean
}

/** 表格列定义（新版） */
export interface GridColumn {
  id: string
  width: number // mm
}

/** 表格行定义 */
export interface GridRow {
  height: number // mm
  cells: Record<string, GridCell> // key = columnId
}

/** 数据表格完整定义 */
export interface TableGrid {
  /** 绑定的数据源 ID */
  dataSourceId: string
  /** 列定义 */
  columns: GridColumn[]
  /** 表头行 */
  headerRow: GridRow
  /** 数据行模板（体表每行按此填充） */
  dataRow: GridRow
  /** 全局默认单元格样式 */
  defaultStyle?: CellStyle
  /** 全局边框预设 */
  borderPreset?: 'none' | 'outline' | 'inside' | 'all'
}

// ============================================================
// 字段绑定
// ============================================================

/** 字段绑定信息 */
export interface FieldBinding {
  dataSource: string
  tableName: string
  fieldName: string
  fieldLabel: string
  fieldType: 'string' | 'number' | 'date' | 'boolean'
}

/** 表格列定义（旧版兼容） */
export interface TableColumn {
  id: string
  header: string
  width: number
  binding?: FieldBinding
  align?: 'left' | 'center' | 'right'
}

/** 数据源分类 */
export type DataSourceCategory = 'document' | 'baseData' | 'report' | 'dynamicForm' | 'systemVar'

/** 数据源定义 */
export interface DataSource {
  id: string
  name: string
  category: DataSourceCategory
  type: 'main' | 'entry' | 'system'
  description: string
  fields: FieldDefinition[]
}

/** 字段定义 */
export interface FieldDefinition {
  name: string
  label: string
  type: 'string' | 'number' | 'date' | 'boolean'
  length?: number
  required?: boolean
}

/** 关联数据源 */
export interface DataSourceRelation {
  parentSource: string
  childSource: string
  parentKey: string
  childKey: string
  description: string
}

/** 页面设置 */
export interface PageSettings {
  width: number; height: number
  marginTop: number; marginRight: number; marginBottom: number; marginLeft: number
  showGrid: boolean; gridSize: number; snapToGrid: boolean
}

// ===== 文档数据模型 =====

export interface DocumentData {
  docId: string
  mainSourceId: string
  /** 明细表数据源 ID，默认 mainSourceId + '_entry' */
  entrySourceId?: string
  header: Record<string, unknown>
  entries: Record<string, unknown>[]
  relatedData: Record<string, Record<string, Record<string, unknown>>>
}

/** 视图模式 */
export type ViewMode = 'design' | 'preview'

// ===== 模板管理 =====

export interface PrintTemplate {
  id: string
  name: string
  businessObjectId: string
  category: DataSourceCategory
  pageSettings: PageSettings
  elements: CanvasElement[]
  createdAt: string
  updatedAt: string
}

/** 设计器全局状态 */
export interface DesignerState {
  viewMode: ViewMode
  documentData: DocumentData | null
  templates: PrintTemplate[]
  currentTemplateId: string | null
  loadTemplate: (templateId: string) => void
  saveCurrentTemplate: () => void
  elements: CanvasElement[]
  selectedElementIds: string[]
  pageSettings: PageSettings
  zoom: number

  selectedGridCell: { elementId: string; rowKey: 'headerRow' | 'dataRow'; columnId: string; rowIndex?: number } | null
  selectGridCell: (elementId: string | null, rowKey?: 'headerRow' | 'dataRow', columnId?: string, rowIndex?: number) => void
  updateGridCell: (elementId: string, rowKey: 'headerRow' | 'dataRow', columnId: string, cell: Partial<GridCell>) => void

  // 元素操作
  addElement: (type: ElementType, x: number, y: number) => void
  updateElement: (id: string, updates: Partial<CanvasElement>) => void
  removeElement: (id: string) => void
  removeSelectedElements: () => void
  selectElement: (id: string, multi?: boolean) => void
  clearSelection: () => void
  setPageSettings: (settings: Partial<PageSettings>) => void
  setZoom: (zoom: number) => void
  bringForward: (id: string) => void
  sendBackward: (id: string) => void
  bringToFront: (id: string) => void
  sendToBack: (id: string) => void
  duplicateElement: (id: string) => void
  resizeElement: (id: string, width: number, height: number, x?: number, y?: number) => void
  _endResize: () => void

  // 撤销/重做
  undo: () => void
  redo: () => void
  canUndo: boolean
  canRedo: boolean

  setViewMode: (mode: ViewMode) => void
  setDocumentData: (data: DocumentData | null) => void
}
