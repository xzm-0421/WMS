import type { ToolboxItem } from '../types/designer'

/** 工具箱元素模板定义 */
export const TOOLBOX_ITEMS: ToolboxItem[] = [
  // ===== 基础控件 =====
  {
    type: 'text',
    label: '静态文本',
    category: 'basic',
    icon: '📝',
    defaultWidth: 60,
    defaultHeight: 8,
  },
  {
    type: 'data-field',
    label: '数据字段',
    category: 'basic',
    icon: '📊',
    defaultWidth: 50,
    defaultHeight: 8,
  },
  {
    type: 'table',
    label: '表格',
    category: 'basic',
    icon: '📋',
    defaultWidth: 160,
    defaultHeight: 40,
  },

  // ===== 绘图控件 =====
  {
    type: 'line',
    label: '线条',
    category: 'drawing',
    icon: '➖',
    defaultWidth: 60,
    defaultHeight: 2,
  },
  {
    type: 'rectangle',
    label: '矩形',
    category: 'drawing',
    icon: '⬜',
    defaultWidth: 80,
    defaultHeight: 60,
  },

  // ===== 高级控件 =====
  {
    type: 'image',
    label: '图片',
    category: 'data',
    icon: '🖼️',
    defaultWidth: 40,
    defaultHeight: 30,
  },
  {
    type: 'barcode',
    label: '条码/二维码',
    category: 'data',
    icon: '🔲',
    defaultWidth: 50,
    defaultHeight: 20,
  },

  // ===== 系统控件 =====
  {
    type: 'page-number',
    label: '页码',
    category: 'system',
    icon: '🔢',
    defaultWidth: 60,
    defaultHeight: 10,
  },
]
