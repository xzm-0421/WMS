import type { DataSource, DataSourceRelation } from '../types/designer'
import { WMS_DATA_SOURCES, WMS_DATA_SOURCE_RELATIONS } from './wmsDataSources'

/** WMS 数据源（字段选择器用） */
export const MOCK_DATA_SOURCES: DataSource[] = WMS_DATA_SOURCES

export const DATA_SOURCE_RELATIONS: DataSourceRelation[] = WMS_DATA_SOURCE_RELATIONS

export const DEFAULT_PAGE_SETTINGS = {
  width: 210,
  height: 297,
  marginTop: 10,
  marginRight: 10,
  marginBottom: 10,
  marginLeft: 10,
  showGrid: true,
  gridSize: 5,
  snapToGrid: true,
}
