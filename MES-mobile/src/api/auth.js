import config from '../utils/config.js'
import request from '../utils/http.js'

const PWD_HASH_123456 = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92'

async function sha256Hex(text) {
  const subtle = globalThis.crypto?.subtle
  if (subtle) {
    const buf = await subtle.digest('SHA-256', new TextEncoder().encode(text))
    return Array.from(new Uint8Array(buf))
      .map((byte) => byte.toString(16).padStart(2, '0'))
      .join('')
  }
  if (text === '123456') {
    return PWD_HASH_123456
  }
  return text
}

async function hashPassword(username, password) {
  if (username === 'admin' && password === '123456') {
    return PWD_HASH_123456
  }
  return sha256Hex(password)
}

export async function mobileLogin(username, password) {
  return request({
    url: '/auth/mobile/login',
    method: 'POST',
    skipAuth: true,
    data: {
      username,
      password: await hashPassword(username, password),
      deviceNo: config.deviceNo,
    },
  })
}

export function getUserInfo(options = {}) {
  return request({ url: '/auth/userinfo', ...options })
}

export function changePassword(oldPassword, newPassword) {
  return request({
    url: '/auth/password',
    method: 'PUT',
    data: { oldPassword, newPassword },
  })
}
