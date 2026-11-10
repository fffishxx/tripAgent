export type TaxiOrderStatus = 'CREATED' | 'COMPLETED' | 'CANCELLED' | string

export interface TaxiPoint {
  name: string
  address?: string | null
  longitude: number
  latitude: number
}

export interface TaxiLocation extends TaxiPoint {
  city: string
}

export interface TaxiEstimateVehicleOption {
  vehicleType: string
  estimatedFare: number
}

export interface TaxiEstimate {
  distanceM: number
  durationMin: number
  currency: string
  mock: boolean
  vehicleOptions: TaxiEstimateVehicleOption[]
}

export interface TaxiEstimatePayload {
  pickup: TaxiPoint
  destination: TaxiPoint
  city?: string
  vehicleType?: string
}

export interface TaxiOrderCreatePayload {
  pickup: TaxiPoint
  destination: TaxiPoint
  city?: string
  vehicleType: string
}

export interface TaxiOrder {
  id: string
  orderNo: string
  status: TaxiOrderStatus
  statusText: string
  idempotentReplay?: boolean | null
  pickup: TaxiPoint
  destination: TaxiPoint
  city?: string | null
  vehicleType?: string | null
  vehicleCategory?: string | null
  distanceM?: number | null
  durationMin?: number | null
  estimatedFare?: number | null
  couponFare?: number | null
  currency?: string | null
  provider?: string | null
  jumpUrl?: string | null
  jumpLinks?: Record<string, string> | null
  cancelReason?: string | null
  cancelledTime?: string | null
  completedTime?: string | null
  createTime: string
  updateTime: string
}

export interface TaxiOrderListQuery {
  pageNum: number
  pageSize: number
  status?: string
}
