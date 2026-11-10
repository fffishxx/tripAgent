export type AmapRouteMode = 'driving' | 'walking'

export interface AmapPoi {
  id: string
  name: string
  type: string
  typeLabel: string
  typecode: string
  address: string
  city: string
  district: string
  longitude: number
  latitude: number
  tel: string
  rating: number | null
  photoUrl: string | null
  itemType: 'ATTRACTION' | 'ACTIVITY' | 'MEAL' | 'HOTEL' | 'TRANSPORT' | 'OTHER'
}

export interface AmapPoiSearchResult {
  pois: AmapPoi[]
  total: number
}

export interface AmapRouteResult {
  distanceM: number
  durationSec: number
  path: Array<[number, number]>
}

export interface AmapRouteSummary {
  distanceM: number
  durationSec: number
}

/** 仅声明本项目使用到的高德 JS API 最小接口。 */
export interface AmapMapInstance {
  add(overlay: unknown | unknown[]): void
  remove(overlay: unknown | unknown[]): void
  setFitView(
    overlayList?: unknown | unknown[],
    immediately?: boolean,
    avoid?: number[],
    maxZoom?: number,
  ): void
  setZoomAndCenter(zoom: number, center: [number, number]): void
  setCenter(center: [number, number]): void
  setZoom(zoom: number, immediately?: boolean): void
  getZoom(): number
  zoomIn(): void
  zoomOut(): void
  resize(): void
  destroy(): void
  on(event: string, handler: () => void): void
}

export interface AmapMarkerInstance {
  on(event: string, handler: () => void): void
}

export interface AmapPolylineInstance {
  // 当前仅作为地图 overlay 使用，无额外方法。
}

export interface AmapNamespace {
  Map: new (
    container: HTMLElement | string,
    options?: Record<string, unknown>,
  ) => AmapMapInstance
  Marker: new (options?: Record<string, unknown>) => AmapMarkerInstance
  Polyline: new (options?: Record<string, unknown>) => AmapPolylineInstance
  Pixel: new (x: number, y: number) => unknown
}
