import { create } from 'zustand'
import { v4 as uuidv4 } from 'uuid'
import type { CanvasElement, DesignerState, ElementType, TableGrid, GridCell } from '../types/designer'
import { TOOLBOX_ITEMS } from '../data/toolboxItems'
import { DEFAULT_PAGE_SETTINGS } from '../data/mockFields'
import { PRESET_TEMPLATES } from '../data/presetTemplates'
import { migrateTableElements } from '../data/tableMigration'

const MAX_HISTORY = 50

/** 创建默认数据表格（完全空白） */
function createDefaultTableGrid(): TableGrid {
  const cols = [
    { id: uuidv4(), width: 40 },
    { id: uuidv4(), width: 40 },
    { id: uuidv4(), width: 40 },
    { id: uuidv4(), width: 40 },
  ]
  const empty = (): GridCell => ({ type: 'text', text: '' })

  return {
    dataSourceId: '',
    columns: cols,
    headerRow: {
      height: 12,
      cells: {
        [cols[0].id]: empty(),
        [cols[1].id]: empty(),
        [cols[2].id]: empty(),
        [cols[3].id]: empty(),
      },
    },
    dataRow: {
      height: 12,
      cells: {
        [cols[0].id]: empty(),
        [cols[1].id]: empty(),
        [cols[2].id]: empty(),
        [cols[3].id]: empty(),
      },
    },
    defaultStyle: {
      fontSize: 10,
      fontFamily: 'SimSun, serif',
      horizontalAlign: 'center',
      verticalAlign: 'middle',
    },
    borderPreset: 'all',
  }
}

/** 根据元素类型创建默认实例 */
function createDefaultElement(type: ElementType, x: number, y: number): CanvasElement {
  const template = TOOLBOX_ITEMS.find((t) => t.type === type)
  const base: CanvasElement = {
    id: uuidv4(),
    type,
    x,
    y,
    width: template?.defaultWidth ?? 60,
    height: template?.defaultHeight ?? 20,
    zIndex: Date.now(),
    locked: false,
    fontSize: 12,
    fontFamily: 'SimSun, serif',
    fontWeight: 'normal',
    fontStyle: 'normal',
    textAlign: 'left',
    color: '#000000',
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: 'transparent',
    borderStyle: 'none',
    padding: 2,
  }

  switch (type) {
    case 'text':
      return { ...base, text: '文本' }
    case 'data-field':
      return { ...base, text: '{{字段}}', borderStyle: 'solid', borderColor: '#cccccc' }
    case 'line':
      return { ...base, height: 2, lineWidth: 1, lineColor: '#000000' }
    case 'rectangle':
      return { ...base, borderStyle: 'solid', borderColor: '#000000', backgroundColor: 'transparent' }
    case 'table':
      return {
        ...base,
        borderStyle: 'solid',
        borderColor: '#000000',
        borderWidth: 1,
        tableGrid: createDefaultTableGrid(),
      }
    case 'image':
      return { ...base, text: '图片', imageFit: 'contain' }
    case 'barcode':
      return { ...base, barcodeType: 'code128', barcodeValue: 'BARCODE' }
    case 'page-number':
      return { ...base, text: '第 1 页 / 共 1 页', pageNumberFormat: '第 {current} 页 / 共 {total} 页' }
    default:
      return base
  }
}

/** 初始元素（从第一个模板加载） */
const initialElements = PRESET_TEMPLATES[0]
  ? migrateTableElements([...PRESET_TEMPLATES[0].elements])
  : []
const initialPageSettings = PRESET_TEMPLATES[0]
  ? { ...PRESET_TEMPLATES[0].pageSettings }
  : { ...DEFAULT_PAGE_SETTINGS }
const initialTemplateId = PRESET_TEMPLATES[0]?.id ?? null

export const useDesignerStore = create<DesignerState>((set, get) => ({
  elements: initialElements,
  selectedElementIds: [],
  pageSettings: initialPageSettings,
  zoom: 1,
  viewMode: 'design',
  documentData: null,

  // ===== 历史栈 =====
  _past: [] as CanvasElement[][],
  _future: [] as CanvasElement[][],

  canUndo: false,
  canRedo: false,

  /** 推入历史栈（在修改 elements 前调用） */
  _pushHistory: () => {
    const { elements, _past } = get()
    const newPast = [..._past, [...elements]]
    // 限制历史栈大小
    if (newPast.length > MAX_HISTORY) newPast.shift()
    set({ _past: newPast, _future: [], canUndo: true, canRedo: false })
  },

  /** 更新 canUndo / canRedo 标记 */
  _updateHistoryFlags: () => {
    const { _past, _future } = get()
    set({ canUndo: _past.length > 0, canRedo: _future.length > 0 })
  },

  // ===== 撤销 / 重做 =====
  undo: () => {
    const { _past, _future, elements } = get()
    if (_past.length === 0) return
    const prev = _past[_past.length - 1]
    const newPast = _past.slice(0, -1)
    const newFuture = [..._future, [...elements]]
    set({
      elements: prev,
      _past: newPast,
      _future: newFuture,
      selectedElementIds: [],
      selectedGridCell: null,
    })
    // 更新标记
    get()._updateHistoryFlags()
  },

  redo: () => {
    const { _past, _future, elements } = get()
    if (_future.length === 0) return
    const next = _future[_future.length - 1]
    const newFuture = _future.slice(0, -1)
    const newPast = [..._past, [...elements]]
    set({
      elements: next,
      _past: newPast,
      _future: newFuture,
      selectedElementIds: [],
      selectedGridCell: null,
    })
    get()._updateHistoryFlags()
  },

  // ===== 表格网格选中 =====
  selectedGridCell: null,

  selectGridCell: (elementId, rowKey, columnId, rowIndex) => {
    set({
      selectedGridCell: elementId && rowKey && columnId
        ? { elementId, rowKey, columnId, rowIndex }
        : null,
    })
  },

  updateGridCell: (elementId, rowKey, columnId, cell) => {
    get()._pushHistory()
    set((s) => ({
      elements: s.elements.map((el) => {
        if (el.id !== elementId || !el.tableGrid) return el
        const grid = { ...el.tableGrid }
        const row = grid[rowKey]
        if (!row) return el
        return {
          ...el,
          tableGrid: {
            ...grid,
            [rowKey]: {
              ...row,
              cells: {
                ...row.cells,
                [columnId]: { ...row.cells[columnId], ...cell },
              },
            },
          },
        }
      }),
    }))
  },

  // ===== 模板管理 =====
  templates: [...PRESET_TEMPLATES],
  currentTemplateId: initialTemplateId,

  loadTemplate: (templateId) => {
    const template = get().templates.find((t) => t.id === templateId)
    if (!template) return
    const entrySourceId = `${template.businessObjectId}_entry`
    // 模板加载不推入历史（相当于全新的起点）
    set({
      currentTemplateId: templateId,
      elements: migrateTableElements([...template.elements], entrySourceId),
      pageSettings: { ...template.pageSettings },
      selectedElementIds: [],
      selectedGridCell: null,
      _past: [],
      _future: [],
      canUndo: false,
      canRedo: false,
    })
  },

  saveCurrentTemplate: () => {
    const { currentTemplateId, elements, pageSettings, templates } = get()
    if (!currentTemplateId) return
    set({
      templates: templates.map((t) =>
        t.id === currentTemplateId
          ? { ...t, elements: [...elements], pageSettings: { ...pageSettings }, updatedAt: new Date().toISOString() }
          : t
      ),
    })
  },

  // ===== 元素操作（所有变更推入历史） =====

  addElement: (type, x, y) => {
    get()._pushHistory()
    const el = createDefaultElement(type, x, y)
    set((s) => ({
      elements: [...s.elements, el],
      selectedElementIds: [el.id],
    }))
  },

  updateElement: (id, updates) => {
    get()._pushHistory()
    set((s) => ({
      elements: s.elements.map((el) =>
        el.id === id ? { ...el, ...updates } : el
      ),
    }))
  },

  removeElement: (id) => {
    get()._pushHistory()
    set((s) => ({
      elements: s.elements.filter((el) => el.id !== id),
      selectedElementIds: s.selectedElementIds.filter((sid) => sid !== id),
    }))
  },

  removeSelectedElements: () => {
    const ids = get().selectedElementIds
    if (ids.length === 0) return
    get()._pushHistory()
    set((s) => ({
      elements: s.elements.filter((el) => !ids.includes(el.id)),
      selectedElementIds: [],
    }))
  },

  selectElement: (id, multi = false) => {
    set((s) => {
      if (!multi) return { selectedElementIds: [id], selectedGridCell: null }
      const exists = s.selectedElementIds.includes(id)
      return {
        selectedElementIds: exists
          ? s.selectedElementIds.filter((sid) => sid !== id)
          : [...s.selectedElementIds, id],
        selectedGridCell: null,
      }
    })
  },

  clearSelection: () => set({ selectedElementIds: [], selectedGridCell: null }),

  setPageSettings: (settings) => {
    set((s) => ({
      pageSettings: { ...s.pageSettings, ...settings },
    }))
  },

  setZoom: (zoom) => set({ zoom: Math.max(0.25, Math.min(2, zoom)) }),

  // ===== 层级操作 =====
  bringForward: (id) => {
    get()._pushHistory()
    set((s) => {
      const el = s.elements.find((e) => e.id === id)
      if (!el) return s
      return {
        elements: s.elements.map((e) =>
          e.id === id ? { ...e, zIndex: Date.now() } : e
        ),
      }
    })
  },

  sendBackward: (id) => {
    get()._pushHistory()
    set((s) => {
      const el = s.elements.find((e) => e.id === id)
      if (!el) return s
      return {
        elements: s.elements.map((e) =>
          e.id === id ? { ...e, zIndex: 0 } : e
        ),
      }
    })
  },

  bringToFront: (id) => {
    get()._pushHistory()
    const maxZ = Math.max(...get().elements.map((e) => e.zIndex), 0)
    set((s) => ({
      elements: s.elements.map((e) =>
        e.id === id ? { ...e, zIndex: maxZ + 1 } : e
      ),
    }))
  },

  sendToBack: (id) => {
    get()._pushHistory()
    const minZ = Math.min(...get().elements.map((e) => e.zIndex), 0)
    set((s) => ({
      elements: s.elements.map((e) =>
        e.id === id ? { ...e, zIndex: minZ - 1 } : e
      ),
    }))
  },

  duplicateElement: (id) => {
    get()._pushHistory()
    set((s) => {
      const el = s.elements.find((e) => e.id === id)
      if (!el) return s
      const newEl: CanvasElement = {
        ...el,
        id: uuidv4(),
        x: el.x + 5,
        y: el.y + 5,
        zIndex: Date.now(),
      }
      return {
        elements: [...s.elements, newEl],
        selectedElementIds: [newEl.id],
      }
    })
  },

  resizeElement: (id, width, height, x, y) => {
    // resizeElement 在拖拽过程中频繁调用，只在首次推历史
    const state = get() as any
    if (!state._resizeHistoryPushed) {
      get()._pushHistory()
      set({ _resizeHistoryPushed: true } as any)
    }
    set((s) => ({
      elements: s.elements.map((el) => {
        if (el.id !== id) return el
        const updates: Partial<CanvasElement> = {
          width: Math.max(5, width),
          height: Math.max(5, height),
        }
        if (x !== undefined) updates.x = Math.max(0, x)
        if (y !== undefined) updates.y = Math.max(0, y)
        return { ...el, ...updates }
      }),
    }))
  },

  /** 缩放结束时清除标记 */
  _endResize: () => {
    set({ _resizeHistoryPushed: false } as any)
  },

  // ===== 视图 + 数据 =====
  setViewMode: (mode) => set({ viewMode: mode }),
  setDocumentData: (data) => set({ documentData: data }),
}))
