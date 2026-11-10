export interface UserPreference {
  crowd: string
  budgetLevel: string
  preferredTransport: string
  travelPace: string
  interests: string[]
  foodPreferences: string[]
  accommodationPreference?: string | null
  notes?: string | null
}
