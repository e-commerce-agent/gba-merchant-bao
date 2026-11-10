/** 展示层格式化工具，只做展示，不做业务判断 */

export function formatMoney(value: number, currency = 'CNY'): string {
  const symbol = currency === 'CNY' ? '¥' : currency === 'HKD' ? 'HK$' : ''
  return `${symbol}${value.toFixed(2)}`
}

export function formatPriceRange(min: number, max: number, currency = 'CNY'): string {
  if (min === max) return formatMoney(min, currency)
  const symbol = currency === 'CNY' ? '¥' : currency === 'HKD' ? 'HK$' : ''
  return `${symbol}${min.toFixed(0)} – ${symbol}${max.toFixed(0)}`
}

export function formatDateTime(input: string): string {
  const d = new Date(input)
  if (Number.isNaN(d.getTime())) return input
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function formatTime(input: string): string {
  const d = new Date(input)
  if (Number.isNaN(d.getTime())) return input
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function relativeTime(input: string): string {
  const d = new Date(input).getTime()
  if (Number.isNaN(d)) return input
  const diff = Date.now() - d
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day} 天前`
  return formatDateTime(input).slice(0, 10)
}

export function maskPhone(phone?: string): string {
  if (!phone) return ''
  return phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
