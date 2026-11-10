export type TripSource = 'AGENT' | 'MANUAL' | string
export type TripStatusKey = 'upcoming' | 'ongoing' | 'completed'

export interface TripStatus {
  key: TripStatusKey
  label: string
}

export interface TripSummary {
  id: string
  title: string
  destination: string
  startDate: string
  endDate: string
  daysCount: number
  source: TripSource
  version: number
  updateTime: string
}

export interface TripItem {
  id: string
  sequenceNo: number
  itemType: string | null
  name: string
  address: string | null
  longitude: number | null
  latitude: number | null
  timeSlot: string | null
  durationMin: number | null
  reason: string | null
  externalPoiId: string | null
  notes: string | null
}

export interface TripDay {
  id: string
  dayIndex: number
  dayDate: string
  theme: string | null
  items: TripItem[]
}

export interface TripLodgingArea {
  name?: string | null
  reason?: string | null
}

export interface TripDiningSuggestion {
  area?: string | null
  cuisine?: string | null
  note?: string | null
}

export interface TripBudgetEstimate {
  total?: string | null
  breakdown?: string | null
}

export interface TripAssumptions {
  days?: number | null
  crowd?: string | null
  budgetLevel?: string | null
  notes?: string[] | null
}

export interface TripWarning {
  dayIndex?: number | null
  from?: string | null
  to?: string | null
  walkMin?: number | null
  distanceKm?: number | null
  message?: string | null
}

export interface TripPlan {
  destination: string
  days: TripDay[]
  lodgingArea?: TripLodgingArea | null
  diningSuggestions?: TripDiningSuggestion[] | null
  budgetEstimate?: TripBudgetEstimate | null
  assumptions?: TripAssumptions | null
  validationStatus?: string | null
  warnings?: TripWarning[] | null
}

export interface TripDetail {
  id: string
  title: string
  destination: string
  startDate: string
  endDate: string
  daysCount: number
  description?: string | null
  source: TripSource
  version: number
  plan: TripPlan
  idempotentReplay?: boolean | null
  createTime: string
  updateTime: string
}

export interface TripItemPayload {
  itemType?: string | null
  name: string
  address?: string | null
  longitude?: number | null
  latitude?: number | null
  timeSlot?: string | null
  durationMin?: number | null
  reason?: string | null
  externalPoiId?: string | null
  notes?: string | null
}

export interface TripDayPayload {
  dayIndex: number
  theme?: string | null
  items: TripItemPayload[]
}

export interface TripPlanPayload {
  destination: string
  days: TripDayPayload[]
  lodgingArea?: TripLodgingArea | null
  diningSuggestions?: TripDiningSuggestion[] | null
  budgetEstimate?: TripBudgetEstimate | null
  assumptions?: TripAssumptions | null
}

export interface TripCreatePayload {
  title?: string
  startDate: string
  endDate: string
  description?: string
  plan: TripPlanPayload
}

export interface TripUpdatePayload extends TripCreatePayload {
  version: number
}

export interface TripDraftItem {
  localId: string
  sourceId: string | null
  itemType: string
  name: string
  address: string
  longitude: number | null
  latitude: number | null
  timeSlot: string
  durationMin: number | null
  reason: string
  notes: string
  externalPoiId: string | null
}

export interface TripDraftDay {
  dayIndex: number
  dayDate: string
  theme: string
  items: TripDraftItem[]
}

export interface TripDraft {
  title: string
  destination: string
  startDate: string
  endDate: string
  description: string
  days: TripDraftDay[]
}
