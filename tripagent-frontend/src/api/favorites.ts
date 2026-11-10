import http from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  FavoriteAddPayload,
  FavoriteAddResult,
  FavoritePlace,
} from '@/types/favorite'

export interface FavoriteListQuery {
  pageNum: number
  pageSize: number
  city?: string
}

const MAX_PAGE_SIZE = 50
const MAX_PAGES = 20

export function getFavorites(
  query: FavoriteListQuery,
  options: { silent?: boolean } = {},
): Promise<PageResult<FavoritePlace>> {
  return http.get<PageResult<FavoritePlace>>('/v1/favorites', {
    params: {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      city: query.city?.trim() || undefined,
    },
    silent: options.silent,
  })
}

export async function getAllFavorites(): Promise<FavoritePlace[]> {
  const first = await getFavorites({
    pageNum: 1,
    pageSize: MAX_PAGE_SIZE,
  })
  const all = [...(first.records ?? [])]
  let pageNum = 1

  while (all.length < first.total && pageNum < MAX_PAGES) {
    pageNum += 1
    const next = await getFavorites({
      pageNum,
      pageSize: MAX_PAGE_SIZE,
    })
    if (!next.records?.length) {
      break
    }
    all.push(...next.records)
  }

  return all
}

export function addFavorite(
  payload: FavoriteAddPayload,
): Promise<FavoriteAddResult> {
  return http.post<FavoriteAddResult>('/v1/favorites', payload)
}

export function removeFavorite(favoriteId: string): Promise<{ favoriteId: string }> {
  return http.delete<{ favoriteId: string }>(`/v1/favorites/${favoriteId}`)
}
