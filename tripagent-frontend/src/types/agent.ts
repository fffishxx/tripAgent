export interface AgentExample {
  title: string
  describe: string
}

export interface AgentSessionInfo {
  sessionId: string
  title: string
  describe: string
  examples?: AgentExample[]
}

export interface AgentTripItem {
  poi?: string
  coords?: [number, number] | null
  time_slot?: string | null
  reason?: string | null
  duration_min?: number | null
  item_type?: string | null
  address?: string | null
  external_poi_id?: string | null
  notes?: string | null
}

export interface AgentTripDay {
  day_index: number
  theme?: string | null
  items?: AgentTripItem[]
}

export interface AgentLodgingArea {
  name?: string | null
  reason?: string | null
}

export interface AgentDiningSuggestion {
  area?: string | null
  cuisine?: string | null
  note?: string | null
}

export interface AgentBudgetEstimate {
  total?: string | null
  breakdown?: string | null
}

export interface AgentAssumptions {
  days?: number | null
  crowd?: string | null
  budget_level?: string | null
  notes?: string[] | null
}

export interface AgentRouteWarning {
  day_index?: number | null
  from?: string | null
  to?: string | null
  walk_min?: number | null
  distance_km?: number | null
  message?: string | null
}

export interface AgentTripPlan {
  destination?: string | null
  days?: AgentTripDay[]
  lodging_area?: AgentLodgingArea | null
  dining_suggestions?: AgentDiningSuggestion[]
  budget_estimate?: AgentBudgetEstimate | null
  assumptions?: AgentAssumptions | null
  validation_status?: string | null
  warnings?: AgentRouteWarning[]
}

export interface AgentValidationResult {
  validation_status?: string | null
  warnings?: AgentRouteWarning[]
  error?: string | null
}

export interface AgentTaxiVehicleOption {
  name: string
  price?: number | string | null
  coupon_price?: number | string | null
  category?: string | null
}

export interface AgentTaxiCard {
  order_id?: string | null
  java_order_id?: string | null
  provider?: string | null
  provider_name?: string | null
  status?: string | null
  status_text?: string | null
  pickup?: string | null
  destination?: string | null
  city?: string | null
  vehicle_type?: string | null
  vehicle_options?: AgentTaxiVehicleOption[]
  distance_km?: number | null
  duration_min?: number | null
  estimated_fare?: number | string | null
  coupon_fare?: number | string | null
  currency?: string | null
  simulated?: boolean
  note?: string | null
  request_id?: string | null
  created_at?: string | null
}

export interface AgentAuthRequired {
  reason?: string
  message?: string
}

export interface AgentSessionDetailMessage {
  type: string
  content: string
  params?: {
    tripPlan?: AgentTripPlan
    taxiOrder?: AgentTaxiCard
    trip_plan?: AgentTripPlan
    taxi_order?: AgentTaxiCard
    [key: string]: unknown
  } | null
}

export interface AgentHistorySession {
  session_id: string
  title: string
  update_time: string
}

export type AgentSessionHistory = Record<string, AgentHistorySession[]> | null

export type AgentSseEvent =
  | { eventType: 1001; eventData: string }
  | { eventType: 1002; eventData?: undefined }
  | { eventType: 1005; eventData: AgentTripPlan }
  | { eventType: 1006; eventData: AgentValidationResult }
  | { eventType: 1007; eventData: AgentTaxiCard }
  | { eventType: 2001; eventData: string }
  | { eventType: 2002; eventData: AgentAuthRequired }

export type ChatMessageRole = 'user' | 'assistant' | 'system'
export type SystemMessageVariant = 'error' | 'info' | 'login'

export interface ChatMessage {
  id: string
  role: ChatMessageRole
  content: string
  tripPlan?: AgentTripPlan | null
  taxiOrder?: AgentTaxiCard | null
  validation?: AgentValidationResult | null
  loginRequired?: AgentAuthRequired | null
  variant?: SystemMessageVariant
  streaming?: boolean
  createdAt: number
}
