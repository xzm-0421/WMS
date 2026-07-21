/**
 * WMS 数据源注册表
 */
import type { DataSource, DataSourceCategory } from '../types/designer'
import { WMS_DATA_SOURCES } from './wmsDataSources'

export const DATA_SOURCE_REGISTRY: DataSource[] = WMS_DATA_SOURCES

export interface DataSourceCategoryMeta {
  key: DataSourceCategory
  label: string
  icon: string
  description: string
}

export const DATA_SOURCE_CATEGORIES: DataSourceCategoryMeta[] = [
  { key: 'document', label: 'WMS 单据', icon: '📋', description: '备料、出入库、盘点等业务单据' },
  { key: 'baseData', label: '基础资料', icon: '📚', description: '物料、供应商、仓库' },
  { key: 'systemVar', label: '系统变量', icon: '⚙️', description: '打印日期、页码等' },
]

export function getDataSourcesByCategory(category: DataSourceCategory): DataSource[] {
  return DATA_SOURCE_REGISTRY.filter((ds) => ds.category === category)
}
