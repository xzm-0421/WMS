import request from '@/utils/request'
import CryptoJS from 'crypto-js'

export interface LoginParams {
  username: string
  password: string
  captcha: string
  captchaKey: string
}

export function sha256(text: string) {
  return CryptoJS.SHA256(text).toString()
}

export function getCaptcha() {
  return request.get<any, { captchaKey: string; captchaImage: string }>('/auth/captcha')
}

export function login(data: LoginParams) {
  return request.post<any, {
    accessToken: string
    refreshToken: string
    expiresIn: number
    userInfo: Record<string, unknown>
  }>('/auth/login', {
    ...data,
    password: sha256(data.password),
  })
}

export function getUserInfo() {
  return request.get<any, Record<string, unknown>>('/auth/userinfo')
}

export function logout() {
  return request.post('/auth/logout')
}
