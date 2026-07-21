import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getUserInfo, login as loginApi, type LoginParams } from '@/api/auth'
import { hasPermission as checkPermission } from '@/utils/permission'

export interface UserInfo {
  userId?: string
  username?: string
  realName?: string
  roles?: string[]
  permissions?: string[]
  warehouseScope?: string[] | null
  dataScope?: number
  isSuperAdmin?: boolean
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('wms_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const roles = computed(() => userInfo.value?.roles ?? [])
  const permissions = computed(() => userInfo.value?.permissions ?? [])
  const isSuperAdmin = computed(
    () => userInfo.value?.isSuperAdmin === true || roles.value.includes('SUPER_ADMIN')
  )

  function hasPermission(code?: string) {
    return checkPermission(permissions.value, roles.value, code)
  }

  async function login(params: LoginParams) {
    const res = await loginApi(params)
    token.value = res.accessToken
    localStorage.setItem('wms_token', res.accessToken)
    localStorage.setItem('wms_refresh_token', res.refreshToken)
    userInfo.value = res.userInfo as UserInfo
    return res
  }

  async function fetchUserInfo() {
    if (!token.value) return
    try {
      userInfo.value = (await getUserInfo()) as UserInfo
    } catch (e) {
      userInfo.value = null
      throw e
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('wms_token')
    localStorage.removeItem('wms_refresh_token')
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    isSuperAdmin,
    hasPermission,
    login,
    fetchUserInfo,
    logout,
  }
})
