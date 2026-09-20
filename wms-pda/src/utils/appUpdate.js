import { getBaseUrl } from './server.js'
import { checkAppUpdate } from '@/api/mobile.js'

const API_SUFFIX = '/api/v1'

function isAppPlus() {
  // eslint-disable-next-line no-undef
  return typeof plus !== 'undefined' && plus?.runtime
}

/**
 * 读取本地应用版本（APP-PLUS 用 getProperty；其它端回退默认值）
 */
export function getLocalAppVersion() {
  return new Promise((resolve) => {
    if (isAppPlus()) {
      try {
        // eslint-disable-next-line no-undef
        plus.runtime.getProperty(plus.runtime.appid, (info) => {
          resolve({
            versionName: info?.version || '1.0.0',
            versionCode: Number(info?.versionCode) || 100,
            name: info?.name || 'WMS PDA',
            platform: 'app',
          })
        })
        return
      } catch {
        // fall through
      }
    }
    resolve({
      versionName: '1.0.0',
      versionCode: 100,
      name: 'WMS PDA',
      platform: 'h5',
    })
  })
}

/**
 * 服务器根地址（不含 /api/v1），用于拼接相对下载路径。
 * 禁止回落到 file:// / HBuilder 调试域，否则 openURL/相对路径会打开 DCloud 网页。
 */
export function getServerRootUrl() {
  const base = getBaseUrl()
  if (!base || base.startsWith('/')) {
    if (
      typeof location !== 'undefined' &&
      location.origin &&
      /^https?:\/\//i.test(location.origin) &&
      !/dcloud|hbuilder|html5plus|uniapp/i.test(location.origin)
    ) {
      return location.origin.replace(/\/+$/, '')
    }
    return ''
  }
  const root = base.replace(API_SUFFIX, '').replace(/\/+$/, '')
  if (!/^https?:\/\//i.test(root)) return ''
  return root
}

/**
 * 将配置中的相对路径拼成绝对 http(s) 地址；拼不出则返回空串（禁止相对路径外开）。
 */
export function resolveDownloadUrl(url) {
  const raw = (url || '').trim()
  if (!raw) return ''
  if (/^https?:\/\//i.test(raw)) return raw
  const root = getServerRootUrl()
  if (!root) return ''
  if (raw.startsWith('/')) return `${root}${raw}`
  return `${root}/${raw}`
}

/**
 * 检测更新：读取本地版本 → 请求服务端 → 返回对比结果
 */
export async function detectAppUpdate() {
  const local = await getLocalAppVersion()
  const remote = await checkAppUpdate(local.versionCode)
  return {
    local,
    remote,
    hasUpdate: !!remote?.hasUpdate,
    downloadUrl: resolveDownloadUrl(remote?.downloadUrl),
  }
}

function installLocalPackage(localPath, isApk) {
  return new Promise((resolve) => {
    // eslint-disable-next-line no-undef
    plus.runtime.install(
      localPath,
      { force: !!isApk },
      () => {
        resolve({
          ok: true,
          message: isApk ? '已调起安装，请按系统提示完成' : '安装成功，即将重启',
        })
        if (!isApk) {
          setTimeout(() => {
            try {
              // eslint-disable-next-line no-undef
              plus.runtime.restart()
            } catch {
              // ignore
            }
          }, 400)
        }
      },
      (e) => {
        resolve({
          ok: false,
          message: `安装失败: ${e?.message || e?.code || '未知错误'}`,
        })
      },
    )
  })
}

function downloadWithUni(url, onProgress) {
  return new Promise((resolve, reject) => {
    const task = uni.downloadFile({
      url,
      success(res) {
        if (res.statusCode === 200 && res.tempFilePath) {
          resolve(res.tempFilePath)
          return
        }
        reject(new Error(`下载失败(${res.statusCode || 0})：${url}`))
      },
      fail(err) {
        reject(new Error(err?.errMsg || `下载失败：${url}`))
      },
    })
    if (task && typeof onProgress === 'function' && task.onProgressUpdate) {
      task.onProgressUpdate((p) => onProgress(p?.progress ?? 0))
    }
  })
}

/** App 端优先 plus.downloader，大文件更稳 */
function downloadWithPlus(url, packageType, onProgress) {
  return new Promise((resolve, reject) => {
    // eslint-disable-next-line no-undef
    if (typeof plus === 'undefined' || !plus.downloader) {
      reject(new Error('plus.downloader 不可用'))
      return
    }
    const ext = String(packageType || '').toLowerCase() === 'apk' || /\.apk(\?|#|$)/i.test(url)
      ? '.apk'
      : '.wgt'
    // eslint-disable-next-line no-undef
    const task = plus.downloader.createDownload(
      url,
      { filename: `_doc/wms-update/${Date.now()}${ext}` },
      (download, status) => {
        if (status === 200 && download?.filename) {
          resolve(download.filename)
          return
        }
        reject(new Error(`下载失败(${status || 0})：${url}`))
      },
    )
    task.addEventListener('statechanged', (d) => {
      if (typeof onProgress !== 'function' || !d || !d.totalSize) return
      const pct = Math.floor((100 * (d.downloadedSize || 0)) / d.totalSize)
      onProgress(Math.max(0, Math.min(100, pct)))
    })
    task.start()
  })
}

/**
 * 下载并安装更新包（仅 APP-PLUS）。
 * 禁止 plus.runtime.openURL：相对路径会落到 HBuilder/DCloud 网页。
 */
export function downloadAndInstallUpdate({ downloadUrl, packageType = 'wgt', onProgress } = {}) {
  return new Promise((resolve) => {
    ;(async () => {
      if (!isAppPlus()) {
        resolve({ ok: false, message: '当前环境不支持安装应用更新，请在 PDA App 中操作' })
        return
      }
      const url = (downloadUrl || '').trim()
      if (!url) {
        resolve({
          ok: false,
          message: '下载地址无效。请在登录页配置完整服务器地址（如 http://IP:9980）',
        })
        return
      }
      if (!/^https?:\/\//i.test(url)) {
        resolve({
          ok: false,
          message: '下载地址必须是 http(s) 完整链接，禁止用相对路径外开浏览器',
        })
        return
      }
      const type = String(packageType || 'wgt').toLowerCase()
      const isApk = type === 'apk' || /\.apk(\?|#|$)/i.test(url)

      let localPath
      try {
        try {
          localPath = await downloadWithPlus(url, type, onProgress)
        } catch {
          localPath = await downloadWithUni(url, onProgress)
        }
      } catch (e) {
        resolve({
          ok: false,
          message: `${e?.message || '下载失败'}。请确认服务器已放置更新包且可访问`,
        })
        return
      }

      const result = await installLocalPackage(localPath, isApk)
      resolve(result)
    })()
  })
}

export default {
  getLocalAppVersion,
  getServerRootUrl,
  resolveDownloadUrl,
  detectAppUpdate,
  downloadAndInstallUpdate,
}
