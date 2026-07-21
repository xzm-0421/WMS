import axios from 'axios'

export async function downloadFile(url: string, filename: string) {
  const token = localStorage.getItem('wms_token')
  const res = await axios.get(`/api/v1${url}`, {
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  const blob = new Blob([res.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}
