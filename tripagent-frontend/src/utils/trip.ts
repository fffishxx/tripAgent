import type {
  TripSource,
  TripStatus,
  TripWarning,
} from '@/types/trip'

export const TIME_SLOT_OPTIONS = ['上午', '中午', '下午', '晚上'] as const

export const ITEM_TYPE_OPTIONS = [
  { value: 'ATTRACTION', label: '景点' },
  { value: 'ACTIVITY', label: '活动' },
  { value: 'MEAL', label: '餐饮' },
  { value: 'HOTEL', label: '住宿' },
  { value: 'TRANSPORT', label: '交通' },
  { value: 'OTHER', label: '其他' },
] as const

export const CROWD_LABELS: Record<string, string> = {
  general: '通用人群',
  family: '家庭出行',
  couple: '情侣出行',
  elderly: '长辈出行',
  solo: '独自出行',
  student: '学生出行',
}

export const BUDGET_LABELS: Record<string, string> = {
  economy: '经济型',
  comfort: '舒适型',
  premium: '高端型',
}

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

export function parseIsoDate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (!match) {
    return null
  }

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))

  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    return null
  }

  return date
}

export function toIsoDate(date: Date): string {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(
    date.getUTCDate(),
  )}`
}

export function getTodayIsoDate(): string {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(
    now.getDate(),
  )}`
}

export function enumerateDateRange(startDate: string, endDate: string): string[] {
  const start = parseIsoDate(startDate)
  const end = parseIsoDate(endDate)
  if (!start || !end || start.getTime() > end.getTime()) {
    return []
  }

  const dates: string[] = []
  const cursor = new Date(start.getTime())
  while (cursor.getTime() <= end.getTime() && dates.length < 1000) {
    dates.push(toIsoDate(cursor))
    cursor.setUTCDate(cursor.getUTCDate() + 1)
  }
  return dates
}

export function formatIsoDateDots(value: string): string {
  const date = parseIsoDate(value)
  if (!date) {
    return value
  }
  return `${date.getUTCFullYear()}.${pad(date.getUTCMonth() + 1)}.${pad(
    date.getUTCDate(),
  )}`
}

export function formatTripDateRange(startDate: string, endDate: string): string {
  if (!startDate || !endDate) {
    return '日期待定'
  }
  if (startDate === endDate) {
    return formatIsoDateDots(startDate)
  }

  const start = parseIsoDate(startDate)
  const end = parseIsoDate(endDate)
  if (!start || !end) {
    return `${startDate} — ${endDate}`
  }

  if (start.getUTCFullYear() === end.getUTCFullYear()) {
    return `${formatIsoDateDots(startDate)} — ${pad(
      end.getUTCMonth() + 1,
    )}.${pad(end.getUTCDate())}`
  }
  return `${formatIsoDateDots(startDate)} — ${formatIsoDateDots(endDate)}`
}

export function formatDayDate(value: string): string {
  const date = parseIsoDate(value)
  if (!date) {
    return value
  }
  return `${date.getUTCMonth() + 1}月${date.getUTCDate()}日 · ${
    WEEKDAY_LABELS[date.getUTCDay()]
  }`
}

export function deriveTripStatus(
  startDate: string,
  endDate: string,
): TripStatus {
  const today = getTodayIsoDate()
  if (today < startDate) {
    return { key: 'upcoming', label: '待出行' }
  }
  if (today <= endDate) {
    return { key: 'ongoing', label: '进行中' }
  }
  return { key: 'completed', label: '已完成' }
}

export function getSourceLabel(source?: TripSource | null): string {
  if (source === 'AGENT') {
    return 'AI 规划'
  }
  if (source === 'MANUAL') {
    return '手动创建'
  }
  return source || '未知来源'
}

export function getItemTypeLabel(itemType?: string | null): string {
  return (
    ITEM_TYPE_OPTIONS.find((option) => option.value === itemType)?.label ||
    '其他'
  )
}

export function getCrowdLabel(crowd?: string | null): string {
  return CROWD_LABELS[crowd || ''] || '未指定'
}

export function getBudgetLevelLabel(level?: string | null): string {
  return BUDGET_LABELS[level || ''] || '未指定'
}

export function getValidationLabel(status?: string | null): string {
  if (status === 'done') {
    return '路线已校验'
  }
  if (status === 'pending') {
    return '路线校验中'
  }
  return '未校验'
}

export function getDayWarnings(
  warnings: TripWarning[] | null | undefined,
  dayIndex: number,
): TripWarning[] {
  return (warnings ?? []).filter((warning) => warning.dayIndex === dayIndex)
}

export function getWarningText(warning: TripWarning): string {
  if (warning.message) {
    return warning.message
  }
  const route = [warning.from, warning.to].filter(Boolean).join(' → ')
  if (route && warning.walkMin) {
    return `${route} 步行约 ${warning.walkMin} 分钟，建议调整`
  }
  return route ? `${route} 路线建议调整` : '路线校验发现潜在问题'
}
