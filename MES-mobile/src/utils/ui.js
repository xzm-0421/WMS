export function comingSoon(name) {
  uni.showToast({ title: `${name}（后续接入）`, icon: 'none' })
}

export function toast(title) {
  uni.showToast({ title, icon: 'none' })
}

/** 新密码：大小写字母 + 数字 + 特殊字符，至少 8 位 */
export const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/

export const PASSWORD_HINT = '密码必须包含大小写字母、数字、特殊字符且至少8位数组成！'
