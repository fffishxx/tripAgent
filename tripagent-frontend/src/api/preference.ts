import http from '@/utils/request'
import type { UserPreference } from '@/types/preference'

export function getPreferences(): Promise<UserPreference> {
  return http.get<UserPreference>('/v1/preferences')
}

export function updatePreferences(
  payload: UserPreference,
): Promise<UserPreference> {
  return http.put<UserPreference>('/v1/preferences', payload)
}
