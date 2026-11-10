import { amapConfig, hasAmapKey } from '@/config/amap'
import type {
  AmapPoi,
  AmapPoiSearchResult,
  AmapRouteMode,
  AmapRouteResult,
} from '@/types/amap'

const AMAP_REST_BASE = 'https://restapi.amap.com/v3'
const REQUEST_TIMEOUT_MS = 15_000

export class AmapConfigError extends Error {
  constructor(message = '未配置高德地图 Key') {
    super(message)
    this.name = 'AmapConfigError'
  }
}

export class AmapServiceError extends Error {
  readonly info?: string
  readonly infocode?: string

  constructor(message: string, info?: string, infocode?: string) {
    super(message)
    this.name = 'AmapServiceError'
    this.info = info
    this.infocode = infocode
  }
}

function textValue(value: unknown): string {
  if (Array.isArray(value)) {
    const first = value.find((item) => typeof item === 'string' && item.trim())
    return typeof first === 'string' ? first : ''
  }
  return typeof value === 'string' ? value : ''
}

function numericValue(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function parseLocation(value: unknown): [number, number] | null {
  if (typeof value !== 'string') {
    return null
  }
  const [longitudeText, latitudeText] = value.split(',')
  const longitude = Number(longitudeText)
  const latitude = Number(latitudeText)
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
    return null
  }
  return [longitude, latitude]
}

function itemTypeFromTypecode(typecode: string): AmapPoi['itemType'] {
  if (typecode.startsWith('05')) {
    return 'MEAL'
  }
  if (typecode.startsWith('10')) {
    return 'HOTEL'
  }
  if (typecode.startsWith('15')) {
    return 'TRANSPORT'
  }
  if (typecode.startsWith('11')) {
    return 'ATTRACTION'
  }
  return 'ACTIVITY'
}

function typeLabelFromType(type: string): string {
  const parts = type.split(';').map((part) => part.trim()).filter(Boolean)
  return parts.length > 0 ? parts[parts.length - 1] : '地点'
}

interface RawAmapPoi {
  id?: unknown
  name?: unknown
  type?: unknown
  typecode?: unknown
  address?: unknown
  cityname?: unknown
  pname?: unknown
  adname?: unknown
  location?: unknown
  tel?: unknown
  photos?: unknown
  biz_ext?: unknown
}

function normalizePoi(raw: RawAmapPoi): AmapPoi | null {
  const location = parseLocation(raw.location)
  const id = textValue(raw.id)
  const name = textValue(raw.name)
  if (!location || !id || !name) {
    return null
  }

  const typecode = textValue(raw.typecode)
  const type = textValue(raw.type)
  const city = textValue(raw.cityname) || textValue(raw.pname)
  const district = textValue(raw.adname)
  const bizExt =
    raw.biz_ext && typeof raw.biz_ext === 'object'
      ? (raw.biz_ext as { rating?: unknown })
      : undefined

  let photoUrl: string | null = null
  if (Array.isArray(raw.photos)) {
    const photo = raw.photos.find(
      (item) =>
        item &&
        typeof item === 'object' &&
        typeof (item as { url?: unknown }).url === 'string',
    ) as { url: string } | undefined
    photoUrl = photo?.url || null
  }

  return {
    id,
    name,
    type,
    typeLabel: typeLabelFromType(type),
    typecode,
    address: textValue(raw.address),
    city,
    district,
    longitude: location[0],
    latitude: location[1],
    tel: textValue(raw.tel),
    rating: numericValue(bizExt?.rating),
    photoUrl,
    itemType: itemTypeFromTypecode(typecode),
  }
}

async function fetchAmapJson<T>(
  path: string,
  params: Record<string, string | number | undefined>,
): Promise<T> {
  if (!hasAmapKey) {
    throw new AmapConfigError(
      '未配置高德地图 Key，请在 .env.development 中设置 VITE_AMAP_KEY',
    )
  }

  const url = new URL(`${AMAP_REST_BASE}${path}`)
  url.searchParams.set('key', amapConfig.key)
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  }

  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    const response = await fetch(url.toString(), {
      signal: controller.signal,
    })
    if (!response.ok) {
      throw new AmapServiceError(
        `高德服务请求失败（HTTP ${response.status}）`,
      )
    }

    const data = (await response.json()) as {
      status?: string
      info?: string
      infocode?: string
    }
    if (data.status !== '1') {
      throw new AmapServiceError(
        data.info || '高德服务返回异常',
        data.info,
        data.infocode,
      )
    }
    return data as T
  } catch (error) {
    if (error instanceof AmapServiceError || error instanceof AmapConfigError) {
      throw error
    }
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new AmapServiceError('高德服务请求超时，请稍后重试')
    }
    throw new AmapServiceError(
      error instanceof Error ? error.message : '高德服务暂时不可用',
    )
  } finally {
    window.clearTimeout(timeout)
  }
}

export interface SearchPoisOptions {
  keywords: string
  city?: string
  types?: string
  page?: number
  pageSize?: number
}

export async function searchPois(
  options: SearchPoisOptions,
): Promise<AmapPoiSearchResult> {
  const data = await fetchAmapJson<{
    count?: string
    pois?: RawAmapPoi[]
  }>('/place/text', {
    keywords: options.keywords.trim(),
    city: options.city?.trim(),
    types: options.types,
    page: options.page || 1,
    offset: options.pageSize || 20,
    extensions: 'all',
    output: 'json',
  })

  const pois = Array.isArray(data.pois)
    ? data.pois
        .map(normalizePoi)
        .filter((poi): poi is AmapPoi => poi !== null)
    : []

  return {
    pois,
    total: numericValue(data.count) ?? pois.length,
  }
}

interface RawRouteStep {
  polyline?: unknown
}

interface RawRoutePath {
  distance?: unknown
  duration?: unknown
  steps?: RawRouteStep[]
}

interface RawRouteResponse {
  route?: {
    paths?: RawRoutePath[]
  }
}

function parsePolyline(polyline: unknown): Array<[number, number]> {
  if (typeof polyline !== 'string' || !polyline.trim()) {
    return []
  }

  return polyline
    .split(';')
    .map((pair) => parseLocation(pair))
    .filter((point): point is [number, number] => point !== null)
}

export interface PlanRouteOptions {
  mode: AmapRouteMode
  origin: [number, number]
  destination: [number, number]
}

export async function planRoute(
  options: PlanRouteOptions,
): Promise<AmapRouteResult> {
  const path = options.mode === 'walking' ? '/direction/walking' : '/direction/driving'
  const data = await fetchAmapJson<RawRouteResponse>(path, {
    origin: `${options.origin[0].toFixed(6)},${options.origin[1].toFixed(6)}`,
    destination: `${options.destination[0].toFixed(6)},${options.destination[1].toFixed(6)}`,
    extensions: 'base',
    output: 'json',
  })

  const routePath = data.route?.paths?.[0]
  if (!routePath) {
    throw new AmapServiceError('未找到可用路线')
  }

  const points: Array<[number, number]> = []
  for (const step of routePath.steps ?? []) {
    for (const point of parsePolyline(step.polyline)) {
      const previous = points[points.length - 1]
      if (
        !previous ||
        previous[0] !== point[0] ||
        previous[1] !== point[1]
      ) {
        points.push(point)
      }
    }
  }

  if (points.length < 2) {
    throw new AmapServiceError('路线结果缺少轨迹点')
  }

  return {
    distanceM: numericValue(routePath.distance) ?? 0,
    durationSec: numericValue(routePath.duration) ?? 0,
    path: points,
  }
}
