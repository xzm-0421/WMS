import axios from 'axios'

async function throwIfJsonErrorBlob(data: Blob) {
  if (!(data instanceof Blob)) {
    return
  }
  const type = (data.type || '').toLowerCase()
  const looksJson = type.includes('application/json') || type.includes('text/plain') || type.includes('text/html')
  if (!looksJson && data.size > 512) {
    return
  }
  // 错误时后端常返回 JSON，但 content-type 可能仍是 octet-stream，抽查文件头
  const head = await data.slice(0, 1).text()
  if (!looksJson && head !== '{' && head !== '[') {
    return
  }
  const text = await data.text()
  try {
    const body = JSON.parse(text) as { message?: string; msg?: string; code?: number }
    // 业务失败或非成功码
    if (body && (body.message || body.msg || body.code !== undefined)) {
      throw new Error(body.message || body.msg || '下载失败')
    }
  } catch (e) {
    if (e instanceof SyntaxError) {
      if (looksJson) {
        throw new Error(text || '下载失败')
      }
      return
    }
    throw e
  }
}

export async function downloadFile(
  url: string,
  filename: string,
  params?: Record<string, string | number | undefined | null>,
) {
  const token = localStorage.getItem('wms_token')
  const res = await axios.get(`/api/v1${url}`, {
    responseType: 'blob',
    params,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (res.status >= 400) {
    throw new Error(`下载失败(${res.status})`)
  }
  await throwIfJsonErrorBlob(res.data)
  const blob = new Blob([res.data], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}

export async function uploadFile<T = unknown>(url: string, file: File, fieldName = 'file') {
  const token = localStorage.getItem('wms_token')
  const form = new FormData()
  form.append(fieldName, file)
  const res = await axios.post(`/api/v1${url}`, form, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'Content-Type': 'multipart/form-data',
    },
  })
  const body = res.data
  if (body?.code !== 200) {
    throw new Error(body?.message || '上传失败')
  }
  return body.data as T
}
