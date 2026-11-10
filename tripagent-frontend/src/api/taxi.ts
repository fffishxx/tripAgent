import http from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  TaxiEstimate,
  TaxiEstimatePayload,
  TaxiOrder,
  TaxiOrderCreatePayload,
  TaxiOrderListQuery,
} from '@/types/taxi'

export function estimateTaxi(
  payload: TaxiEstimatePayload,
): Promise<TaxiEstimate> {
  return http.post<TaxiEstimate>('/v1/taxi/estimates', payload, {
    silent: true,
  })
}

export function createTaxiOrder(
  payload: TaxiOrderCreatePayload,
  options: { idempotencyKey?: string } = {},
): Promise<TaxiOrder> {
  return http.post<TaxiOrder>('/v1/taxi/orders', payload, {
    headers: options.idempotencyKey
      ? { 'Idempotency-Key': options.idempotencyKey }
      : undefined,
  })
}

export function getTaxiOrders(
  query: TaxiOrderListQuery,
  options: { silent?: boolean } = {},
): Promise<PageResult<TaxiOrder>> {
  return http.get<PageResult<TaxiOrder>>('/v1/taxi/orders', {
    params: {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
    },
    silent: options.silent,
  })
}

export function getTaxiOrder(orderId: string): Promise<TaxiOrder> {
  return http.get<TaxiOrder>(`/v1/taxi/orders/${orderId}`)
}

export function cancelTaxiOrder(
  orderId: string,
  reason?: string,
): Promise<TaxiOrder> {
  return http.post<TaxiOrder>(`/v1/taxi/orders/${orderId}/cancel`, {
    reason: reason?.trim() || null,
  })
}

export function completeTaxiOrder(orderId: string): Promise<TaxiOrder> {
  return http.post<TaxiOrder>(`/v1/taxi/orders/${orderId}/complete`)
}
