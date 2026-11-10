import http from '@/utils/request'
import type {
  TripCreatePayload,
  TripDetail,
  TripSummary,
  TripUpdatePayload,
} from '@/types/trip'
import type { PageResult } from '@/types/api'

export interface TripListQuery {
  pageNum: number
  pageSize: number
  keyword?: string
}

export function getTrips(
  query: TripListQuery,
  options: { silent?: boolean } = {},
): Promise<PageResult<TripSummary>> {
  return http.get<PageResult<TripSummary>>('/v1/trips', {
    params: {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword?.trim() || undefined,
    },
    silent: options.silent,
  })
}

export async function getAllTrips(): Promise<TripSummary[]> {
  const first = await getTrips({
    pageNum: 1,
    pageSize: 50,
  })
  const all = [...(first.records ?? [])]
  let pageNum = 1

  while (all.length < first.total && pageNum < 20) {
    pageNum += 1
    const next = await getTrips({
      pageNum,
      pageSize: 50,
    })
    if (!next.records?.length) {
      break
    }
    all.push(...next.records)
  }

  return all
}

export function getTripDetail(tripId: string): Promise<TripDetail> {
  return http.get<TripDetail>(`/v1/trips/${tripId}`)
}

export function createTrip(
  payload: TripCreatePayload,
  options: { idempotencyKey?: string } = {},
): Promise<TripDetail> {
  return http.post<TripDetail>('/v1/trips', payload, {
    headers: options.idempotencyKey
      ? { 'Idempotency-Key': options.idempotencyKey }
      : undefined,
  })
}

export function updateTrip(
  tripId: string,
  payload: TripUpdatePayload,
): Promise<TripDetail> {
  return http.put<TripDetail>(`/v1/trips/${tripId}`, payload, {
    silent: true,
  })
}

export function deleteTrip(tripId: string): Promise<{ tripId: string }> {
  return http.delete<{ tripId: string }>(`/v1/trips/${tripId}`)
}
