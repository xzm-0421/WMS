import request from '@/utils/request'

export interface PermissionItem {
  id: number
  permissionCode: string
  permissionName: string
  module: string
}

export interface PermissionModule {
  module: string
  moduleName: string
  permissions: PermissionItem[]
}

export function getPermissionModules() {
  return request.get<any, PermissionModule[]>('/system/permissions')
}
