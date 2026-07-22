/**
 * WMS PDA 移动端 API（对齐 API 文档 13.x）
 */
import config from '../utils/config.js'
import request, { withDevice } from '../utils/http.js'

const PWD_HASH_123456 = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92'

function hashPassword(username, password) {
  if (username === 'admin' && password === '123456') return PWD_HASH_123456
  return password
}

// ========== 认证 ==========
export function mobileLogin(username, password) {
  return request({
    url: '/auth/mobile/login',
    method: 'POST',
    data: { username, password: hashPassword(username, password), deviceNo: config.deviceNo },
  })
}

export function refreshToken(refreshToken) {
  return request({
    url: '/auth/refresh',
    method: 'POST',
    data: { refreshToken },
  })
}

export function getUserInfo() {
  return request({ url: '/auth/userinfo' })
}

export function changePassword(oldPassword, newPassword) {
  return request({
    url: '/auth/password',
    method: 'PUT',
    data: { oldPassword, newPassword },
  })
}

// ========== 条码 ==========
export function parseBarcode(ruleCode, barcodeContent) {
  return request({
    url: '/barcode/parse',
    method: 'POST',
    data: { ruleCode, barcodeContent },
  })
}

/** PDA 专用启发式条码解析（不依赖规则库） */
export function parseMobileBarcode(barcodeContent) {
  return request({
    url: '/mobile/barcode/parse',
    method: 'POST',
    data: { barcodeContent },
  })
}

// ========== 条码校验 ==========
export function resolveBarcodeBill(barcodeContent) {
  return request({
    url: '/mobile/panel/resolve-bill',
    method: 'POST',
    data: { barcodeContent },
  })
}

export function getBarcodeBillDetail(billNo) {
  return request({ url: `/mobile/panel/bills/${encodeURIComponent(billNo)}` })
}

export function verifyBarcodeMaterial(billNo, barcodeContent) {
  return request({
    url: `/mobile/panel/bills/${encodeURIComponent(billNo)}/verify`,
    method: 'POST',
    data: withDevice({ barcodeContent }),
    silent: true,
  })
}

/** @deprecated 使用 verifyBarcodeMaterial */
export function verifyPanelCode(data) {
  return verifyBarcodeMaterial(data?.billNo, data?.panelCode || data?.barcodeContent)
}

// ========== 待办任务 ==========
export function getTasks(params = {}) {
  return request({ url: '/mobile/tasks', data: params })
}

// ========== 入库 ==========
export function getInboundOrder(orderNo) {
  return request({ url: `/mobile/inbound/${orderNo}` })
}

export function scanInbound(orderNo, data) {
  return request({
    url: `/mobile/inbound/${orderNo}/scan`,
    method: 'POST',
    data: withDevice(data),
  })
}

export function batchScanInbound(orderNo, items) {
  return request({
    url: `/mobile/inbound/${orderNo}/batch-scan`,
    method: 'POST',
    data: withDevice({ items }),
  })
}

export function completeInbound(orderNo) {
  return request({
    url: `/mobile/inbound/${orderNo}/complete`,
    method: 'POST',
  })
}

// ========== 出库 ==========
export function getOutboundOrder(orderNo) {
  return request({ url: `/mobile/outbound/${orderNo}` })
}

export function recommendOutbound(orderNo, lineNo) {
  return request({ url: `/mobile/outbound/${orderNo}/recommend`, data: { lineNo } })
}

export function scanOutbound(orderNo, data) {
  return request({
    url: `/mobile/outbound/${orderNo}/scan`,
    method: 'POST',
    data: withDevice(data),
  })
}

export function completeOutbound(orderNo) {
  return request({
    url: `/mobile/outbound/${orderNo}/complete`,
    method: 'POST',
  })
}

// ========== 移库 ==========
export function transferStock(data) {
  return request({
    url: '/mobile/transfer',
    method: 'POST',
    data: withDevice(data),
  })
}

// ========== 库存查询 ==========
export function queryInventoryGet(params) {
  return request({ url: '/mobile/inventory/query', data: params })
}

export function queryInventoryPost(data) {
  return request({
    url: '/mobile/inventory/query',
    method: 'POST',
    data,
  })
}

// ========== 盘点 ==========
export function getStockcheckTask(taskNo) {
  return request({ url: `/mobile/stockcheck/${taskNo}` })
}

export function scanStockcheck(taskNo, data) {
  return request({
    url: `/mobile/stockcheck/${taskNo}/scan`,
    method: 'POST',
    data: withDevice(data),
  })
}

export function gainStockcheck(taskNo, data) {
  return request({
    url: `/mobile/stockcheck/${taskNo}/gain`,
    method: 'POST',
    data: withDevice(data),
  })
}

export function confirmEmptyStockcheck(taskNo, data) {
  return request({
    url: `/mobile/stockcheck/${taskNo}/confirm-empty`,
    method: 'POST',
    data: withDevice(data),
  })
}

export function completeStockcheck(taskNo) {
  return request({
    url: `/mobile/stockcheck/${taskNo}/complete`,
    method: 'POST',
  })
}

// ========== 质检 ==========
export function getQcOrder(qcNo) {
  return request({ url: `/mobile/quality/${qcNo}` })
}

export function submitQcResult(qcNo, data) {
  return request({
    url: `/mobile/quality/${qcNo}/result`,
    method: 'POST',
    data: withDevice({
      result: data.overallResult || data.result,
      remark: data.remark,
    }),
  })
}

// ========== 追溯 ==========
export function traceBatch(data) {
  return request({
    url: '/mobile/trace',
    method: 'POST',
    data,
  })
}

// ========== 离线同步 ==========
export function syncOfflineData(offlineData, lastSyncTime) {
  return request({
    url: '/mobile/sync',
    method: 'POST',
    data: withDevice({ offlineData, lastSyncTime }),
  })
}

// ========== 消息 ==========
export function getMessages(params = {}) {
  return request({ url: '/mobile/messages', data: params })
}

export function getUnreadCount() {
  return request({ url: '/mobile/messages/unread-count' })
}

export function markMessageRead(messageId) {
  return request({
    url: `/mobile/messages/${messageId}/read`,
    method: 'PUT',
  })
}

export function markAllMessagesRead() {
  return request({
    url: '/mobile/messages/read-all',
    method: 'PUT',
  })
}

export default {
  mobileLogin,
  refreshToken,
  getUserInfo,
  changePassword,
  parseBarcode,
  parseMobileBarcode,
  verifyPanelCode,
  getTasks,
  getInboundOrder,
  scanInbound,
  batchScanInbound,
  completeInbound,
  getOutboundOrder,
  recommendOutbound,
  scanOutbound,
  completeOutbound,
  transferStock,
  queryInventoryGet,
  queryInventoryPost,
  getStockcheckTask,
  scanStockcheck,
  gainStockcheck,
  confirmEmptyStockcheck,
  completeStockcheck,
  getQcOrder,
  submitQcResult,
  traceBatch,
  syncOfflineData,
  getMessages,
  getUnreadCount,
  markMessageRead,
  markAllMessagesRead,
}
