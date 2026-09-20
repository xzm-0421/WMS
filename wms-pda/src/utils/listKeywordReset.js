/**
 * 明细页提交成功返回列表时：清空列表页搜索框中的单号。
 * 列表 onShow 通过 consume 消费标记；手动返回不清空。
 */

const STORAGE_KEY = 'wms_pda_clear_list_keyword'

export function markClearListKeywordAfterSubmit() {
  try {
    uni.setStorageSync(STORAGE_KEY, '1')
  } catch {
    // ignore
  }
}

/** @returns {boolean} 是否需要清空 keyword 并刷新列表 */
export function consumeClearListKeyword() {
  try {
    const flag = uni.getStorageSync(STORAGE_KEY)
    if (flag) {
      uni.removeStorageSync(STORAGE_KEY)
      return true
    }
  } catch {
    // ignore
  }
  return false
}

/** 提交成功后返回上一页（列表），并标记清空搜索单号 */
export function navigateBackAfterSubmit(delayMs = 400) {
  markClearListKeywordAfterSubmit()
  setTimeout(() => uni.navigateBack({ fail: () => {} }), delayMs)
}

export default {
  markClearListKeywordAfterSubmit,
  consumeClearListKeyword,
  navigateBackAfterSubmit,
}
