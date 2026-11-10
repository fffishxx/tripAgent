export interface FavoritePlace {
  id: string
  placeKey: string
  externalPoiId?: string | null
  name: string
  city?: string | null
  address?: string | null
  longitude?: number | null
  latitude?: number | null
  category?: string | null
  typecode?: string | null
  note?: string | null
  source?: string | null
  createTime?: string
}

export interface FavoriteAddResult extends FavoritePlace {
  duplicate?: boolean
}

export interface FavoriteAddPayload {
  externalPoiId?: string | null
  name: string
  city?: string | null
  address?: string | null
  longitude?: number | null
  latitude?: number | null
  category?: string | null
  typecode?: string | null
  note?: string | null
  source?: 'TRIP' | 'SEARCH' | 'MANUAL'
}
