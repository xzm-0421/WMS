import request from '@/utils/request'
import type { PageQuery, PageResult } from './types'

export interface SysUser {
  id?: number
  username: string
  realName: string
  phone?: string
  email?: string
  deptId?: number
  status?: number
  roleIds?: number[]
  password?: string
}

export function getUsers(params: PageQuery) {
  return request.get<any, PageResult<SysUser>>('/system/users', { params })
}

export function getUser(userId: number) {
  return request.get<any, SysUser>(`/system/users/${userId}`)
}

export function createUser(data: SysUser) {
  return request.post('/system/users', data)
}

export function updateUser(userId: number, data: SysUser) {
  return request.put(`/system/users/${userId}`, data)
}

export function deleteUser(userId: number) {
  return request.delete(`/system/users/${userId}`)
}

export function resetPassword(userId: number, password: string) {
  return request.put(`/system/users/${userId}/reset-password`, { password })
}

export function updateUserStatus(userId: number, status: number) {
  return request.put(`/system/users/${userId}/status`, { status })
}
