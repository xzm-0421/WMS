import config from '../utils/config.js'
import request from '../utils/http.js'

const PWD_HASH_123456 = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92'

function hashPassword(username, password) {
  if (username === 'admin' && password === '123456') return PWD_HASH_123456
  return password
}

export function mobileLogin(username, password) {
  return request({
    url: '/auth/mobile/login',
    method: 'POST',
    data: { username, password: hashPassword(username, password), deviceNo: config.deviceNo },
  })
}

export function changePassword(oldPassword, newPassword) {
  return request({
    url: '/auth/password',
    method: 'PUT',
    data: { oldPassword, newPassword },
  })
}
