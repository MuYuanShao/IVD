export function saveBusinessFile(blob, filename) {
  if (!(blob instanceof Blob) || blob.size === 0) throw new Error('未收到导出文件')
  if (blob.type.includes('json') || blob.type.includes('html')) throw new Error('服务器未返回有效导出文件')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.append(link)
  try { link.click() } finally {
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
}
