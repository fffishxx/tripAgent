import type {
  TaxiLocation,
  TaxiOrderStatus,
  TaxiPoint,
} from '@/types/taxi'

export interface TaxiStatusFilter {
  key: string
  label: string
}

export const TAXI_STATUS_FILTERS: TaxiStatusFilter[] = [
  { key: '', label: '全部' },
  { key: 'CREATED', label: '进行中' },
  { key: 'COMPLETED', label: '已完成' },
  { key: 'CANCELLED', label: '已取消' },
]

export function getTaxiStatusLabel(
  status: TaxiOrderStatus,
  statusText?: string | null,
): string {
  if (status === 'CREATED') {
    return '进行中'
  }
  if (status === 'COMPLETED') {
    return '已完成'
  }
  if (status === 'CANCELLED') {
    return '已取消'
  }
  return statusText || status
}

export function getTaxiStatusTone(
  status: TaxiOrderStatus,
): 'ongoing' | 'success' | 'muted' {
  if (status === 'CREATED') {
    return 'ongoing'
  }
  if (status === 'COMPLETED') {
    return 'success'
  }
  return 'muted'
}

export function getTaxiProviderLabel(provider?: string | null): string {
  if (provider === 'MOCK' || !provider) {
    return 'Java 模拟打车'
  }
  if (provider === 'DIDI_MCP') {
    return '滴滴（模拟分支）'
  }
  return provider
}

export function formatTaxiDistance(distanceM?: number | null): string {
  if (distanceM === null || distanceM === undefined) {
    return '--'
  }
  if (distanceM < 1000) {
    return `${distanceM} 米`
  }
  return `${(distanceM / 1000).toFixed(1)} 公里`
}

export function formatTaxiDuration(durationMin?: number | null): string {
  if (durationMin === null || durationMin === undefined) {
    return '--'
  }
  if (durationMin < 60) {
    return `${durationMin} 分钟`
  }
  const hours = Math.floor(durationMin / 60)
  const minutes = durationMin % 60
  return minutes > 0 ? `${hours} 小时 ${minutes} 分钟` : `${hours} 小时`
}

export function toTaxiPoint(location: TaxiLocation): TaxiPoint {
  return {
    name: location.name,
    address: location.address || null,
    longitude: location.longitude,
    latitude: location.latitude,
  }
}

export function sameTaxiPoint(
  pickup: TaxiPoint | null,
  destination: TaxiPoint | null,
): boolean {
  if (!pickup || !destination) {
    return false
  }
  return (
    Math.abs(pickup.longitude - destination.longitude) < 0.000001 &&
    Math.abs(pickup.latitude - destination.latitude) < 0.000001
  )
}
