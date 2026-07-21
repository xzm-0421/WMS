import request from '@/utils/request'

export interface KingdeeMasterDataSyncResult {
  totalFetched: number
  inserted: number
  updated: number
  skipped: number
  message: string
}

export function syncKingdeeMaterials(keyword?: string) {
  return request.post<any, KingdeeMasterDataSyncResult>(
    '/integration/kingdee/master-data/sync/materials',
    null,
    { params: keyword ? { keyword } : undefined },
  )
}

export function syncKingdeeWarehouses(keyword?: string) {
  return request.post<any, KingdeeMasterDataSyncResult>(
    '/integration/kingdee/master-data/sync/warehouses',
    null,
    { params: keyword ? { keyword } : undefined },
  )
}

export function syncKingdeeMasterData(keyword?: string) {
  return request.post<any, { materials: KingdeeMasterDataSyncResult; warehouses: KingdeeMasterDataSyncResult; message: string }>(
    '/integration/kingdee/master-data/sync/all',
    null,
    { params: keyword ? { keyword } : undefined },
  )
}
