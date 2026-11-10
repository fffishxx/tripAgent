export function maskPhone(phone: string): string {
  if (!phone) {
    return ''
  }
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone
}

export function getAvatarText(name: string): string {
  const trimmed = name.trim()
  return trimmed ? trimmed.slice(0, 1).toUpperCase() : '游'
}

export function createId(prefix = ''): string {
  const randomPart =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID().replaceAll('-', '')
      : `${Date.now()}${Math.random().toString(16).slice(2)}`

  return `${prefix}${randomPart}`
}

export function formatDateTime(value: string): string {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const pad = (part: number): string => String(part).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`
}

export function formatDuration(minutes?: number | null): string {
  if (!minutes || minutes <= 0) {
    return ''
  }
  if (minutes < 60) {
    return `约 ${minutes} 分钟`
  }

  const hours = Math.floor(minutes / 60)
  const remaining = minutes % 60
  return remaining > 0
    ? `约 ${hours} 小时 ${remaining} 分钟`
    : `约 ${hours} 小时`
}

export function formatFare(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') {
    return '--'
  }

  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric)) {
    return String(value)
  }
  return `¥${numeric.toFixed(2)}`
}
