import type { UserPreference } from '@/types/preference'

export interface PreferenceOption {
  value: string
  label: string
  description?: string
}

export const CROWD_OPTIONS: PreferenceOption[] = [
  { value: 'general', label: '通用人群' },
  { value: 'family', label: '家庭出行' },
  { value: 'couple', label: '情侣出行' },
  { value: 'elderly', label: '长辈同行' },
  { value: 'solo', label: '独自旅行' },
  { value: 'student', label: '学生出行' },
]

export const BUDGET_OPTIONS: PreferenceOption[] = [
  { value: 'economy', label: '经济型' },
  { value: 'comfort', label: '舒适型' },
  { value: 'premium', label: '高端型' },
]

export const TRANSPORT_OPTIONS: PreferenceOption[] = [
  { value: 'transit', label: '公共交通优先' },
  { value: 'walking', label: '步行为主' },
  { value: 'driving', label: '驾车为主' },
]

export const PACE_OPTIONS: PreferenceOption[] = [
  { value: 'relaxed', label: '轻松慢节奏' },
  { value: 'moderate', label: '适中节奏' },
  { value: 'intensive', label: '紧凑高效' },
]

export const INTEREST_SUGGESTIONS = [
  '历史人文',
  '自然风光',
  '美食探店',
  '博物馆',
  '摄影',
  '购物',
  '夜生活',
  '亲子',
  '户外',
  '文化演出',
]

export const FOOD_SUGGESTIONS = [
  '不吃辣',
  '素食',
  '不吃海鲜',
  '清真',
  '少油少盐',
  '不吃香菜',
  '不吃牛肉',
  '过敏忌口',
]

export function createEmptyPreference(): UserPreference {
  return {
    crowd: 'general',
    budgetLevel: 'economy',
    preferredTransport: 'transit',
    travelPace: 'moderate',
    interests: [],
    foodPreferences: [],
    accommodationPreference: '',
    notes: '',
  }
}

export interface SanitizedTags {
  values: string[]
  error: string | null
}

export function sanitizeTags(
  values: string[],
  maxLength: number,
  fieldLabel: string,
): SanitizedTags {
  const result: string[] = []
  const seen = new Set<string>()

  for (const value of values) {
    const trimmed = value.trim()
    if (!trimmed) {
      continue
    }
    if (trimmed.length > maxLength) {
      return {
        values: [],
        error: `${fieldLabel}每项最多 ${maxLength} 个字符`,
      }
    }
    if (!seen.has(trimmed)) {
      seen.add(trimmed)
      result.push(trimmed)
    }
  }

  if (result.length > 10) {
    return {
      values: [],
      error: `${fieldLabel}最多 10 项`,
    }
  }

  return { values: result, error: null }
}
