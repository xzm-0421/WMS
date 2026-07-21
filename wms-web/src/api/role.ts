import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface SysRole {
  id?: number
  roleCode: string
  roleName: string
  description?: string
  dataScope?: number
  status?: number
  permissionIds?: number[]
  permissionCodes?: string[]
  warehouseScope?: string[]
}

export interface RolePermissionPayload {
  permissionCodes: string[]
  dataScope: number
  warehouseScope?: string[]
}

export function getRoles(params: PageQuery) {
  return request.get<any, PageResult<SysRole>>('/system/roles', { params })
}

export function getRole(roleId: number) {
  return request.get<any, SysRole>(`/system/roles/${roleId}`)
}

export function createRole(data: SysRole) {
  return request.post('/system/roles', data)
}

export function updateRole(roleId: number, data: SysRole) {
  return request.put(`/system/roles/${roleId}`, data)
}

export function deleteRole(roleId: number) {
  return request.delete(`/system/roles/${roleId}`)
}

export function assignRolePermissions(roleId: number, data: RolePermissionPayload) {
  return request.put(`/system/roles/${roleId}/permissions`, data)
}
