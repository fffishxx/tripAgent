<script setup lang="ts">
import {
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import { Aim, Minus, Plus, WarningFilled } from '@element-plus/icons-vue'
import { AmapLoadError, loadAmap } from '@/utils/amap-loader'
import type {
  AmapMapInstance,
  AmapNamespace,
  AmapPoi,
} from '@/types/amap'

const props = withDefaults(
  defineProps<{
    pois?: AmapPoi[]
    selectedPoiId?: string | null
    routePath?: Array<[number, number]>
    routeStart?: AmapPoi | null
    routeEnd?: AmapPoi | null
  }>(),
  {
    pois: () => [],
    selectedPoiId: null,
    routePath: () => [],
    routeStart: null,
    routeEnd: null,
  },
)

const emit = defineEmits<{
  'poi-click': [poi: AmapPoi]
  ready: []
  error: [message: string]
}>()

const DEFAULT_CENTER: [number, number] = [120.15507, 30.274084]

const containerRef = ref<HTMLDivElement | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const errorCode = ref<'MISSING_KEY' | 'LOAD_FAILED' | ''>('')
const mapReady = ref(false)

let amap: AmapNamespace | null = null
let map: AmapMapInstance | null = null
let resizeObserver: ResizeObserver | null = null
let disposed = false
let initializing = false
let poiMarkers: unknown[] = []
let routeOverlays: unknown[] = []

function clearPoiMarkers(): void {
  if (map && poiMarkers.length) {
    map.remove(poiMarkers)
  }
  poiMarkers = []
}

function renderPoiMarkers(fitView: boolean): void {
  if (!map || !amap) {
    return
  }

  clearPoiMarkers()
  const markers = props.pois.map((poi, index) => {
    const content = document.createElement('div')
    content.className = `amap-poi-pin${
      poi.id === props.selectedPoiId ? ' is-selected' : ''
    }`
    content.textContent = String(index + 1)
    content.title = poi.name

    const marker = new amap!.Marker({
      position: [poi.longitude, poi.latitude],
      content,
      offset: new amap!.Pixel(-14, -14),
      zIndex: 100,
      title: poi.name,
    })
    marker.on('click', () => emit('poi-click', poi))
    return marker
  })

  poiMarkers = markers
  if (markers.length) {
    map.add(markers)
    if (fitView) {
      map.setFitView(markers, false, [80, 80, 80, 80], 15)
    }
  }
}

function clearRoute(): void {
  if (map && routeOverlays.length) {
    map.remove(routeOverlays)
  }
  routeOverlays = []
}

function renderRoute(fitView: boolean): void {
  if (!map || !amap) {
    return
  }

  clearRoute()
  if (props.routePath.length < 2) {
    return
  }

  const overlays: unknown[] = []
  const polyline = new amap.Polyline({
    path: props.routePath,
    strokeColor: '#2f7ef7',
    strokeWeight: 6,
    strokeOpacity: 0.92,
    lineJoin: 'round',
    lineCap: 'round',
    zIndex: 120,
  })
  overlays.push(polyline)

  if (props.routeStart) {
    const content = document.createElement('div')
    content.className = 'amap-route-marker amap-route-marker--start'
    content.textContent = '起'
    content.title = props.routeStart.name
    overlays.push(
      new amap.Marker({
        position: [props.routeStart.longitude, props.routeStart.latitude],
        content,
        offset: new amap.Pixel(-12, -12),
        zIndex: 140,
        title: props.routeStart.name,
      }),
    )
  }

  if (props.routeEnd) {
    const content = document.createElement('div')
    content.className = 'amap-route-marker amap-route-marker--end'
    content.textContent = '终'
    content.title = props.routeEnd.name
    overlays.push(
      new amap.Marker({
        position: [props.routeEnd.longitude, props.routeEnd.latitude],
        content,
        offset: new amap.Pixel(-12, -12),
        zIndex: 140,
        title: props.routeEnd.name,
      }),
    )
  }

  routeOverlays = overlays
  map.add(overlays)
  if (fitView) {
    map.setFitView(overlays, false, [100, 100, 100, 100], 16)
  }
}

async function initMap(): Promise<void> {
  if (initializing) {
    return
  }

  initializing = true
  destroyMap()
  errorMessage.value = ''
  errorCode.value = ''
  loading.value = true
  mapReady.value = false

  try {
    amap = await loadAmap()
    if (disposed) {
      return
    }

    if (!containerRef.value) {
      throw new Error('地图容器尚未挂载')
    }

    map = new amap.Map(containerRef.value, {
      zoom: 12,
      center: DEFAULT_CENTER,
      viewMode: '2D',
      resizeEnable: true,
    })

    renderPoiMarkers(true)
    renderRoute(true)

    resizeObserver = new ResizeObserver(() => {
      map?.resize()
    })
    resizeObserver.observe(containerRef.value)

    mapReady.value = true
    loading.value = false
    emit('ready')
  } catch (error) {
    loading.value = false
    errorCode.value =
      error instanceof AmapLoadError ? error.code : 'LOAD_FAILED'
    errorMessage.value =
      error instanceof Error ? error.message : '地图加载失败，请稍后重试'
    emit('error', errorMessage.value)
    destroyMap()
  } finally {
    initializing = false
  }
}

function destroyMap(): void {
  resizeObserver?.disconnect()
  resizeObserver = null
  clearPoiMarkers()
  clearRoute()
  if (map) {
    map.destroy()
    map = null
  }
  amap = null
  mapReady.value = false
}

function zoomIn(): void {
  map?.zoomIn()
}

function zoomOut(): void {
  map?.zoomOut()
}

function resetView(): void {
  if (!map) {
    return
  }
  if (routeOverlays.length) {
    map.setFitView(routeOverlays, false, [100, 100, 100, 100], 16)
    return
  }
  if (poiMarkers.length) {
    map.setFitView(poiMarkers, false, [80, 80, 80, 80], 15)
    return
  }
  map.setZoomAndCenter(12, DEFAULT_CENTER)
}

function panTo(longitude: number, latitude: number): void {
  map?.setCenter([longitude, latitude])
}

watch(
  () => props.pois,
  () => renderPoiMarkers(true),
  { deep: false },
)

watch(
  () => props.selectedPoiId,
  () => renderPoiMarkers(false),
)

watch(
  () => props.routePath,
  () => renderRoute(true),
  { deep: false },
)

onMounted(() => {
  void initMap()
})

onBeforeUnmount(() => {
  disposed = true
  destroyMap()
})

defineExpose({
  zoomIn,
  zoomOut,
  resetView,
  panTo,
})
</script>

<template>
  <div class="amap-shell">
    <div ref="containerRef" class="amap-container" aria-label="高德地图" />

    <div v-if="loading" class="amap-overlay amap-overlay--loading">
      <span class="amap-spinner" aria-hidden="true" />
      <strong>正在加载高德地图</strong>
      <p>首次加载需要下载地图资源，请稍候…</p>
    </div>

    <div v-else-if="errorMessage" class="amap-overlay amap-overlay--error">
      <span class="amap-overlay__icon">
        <el-icon><WarningFilled /></el-icon>
      </span>
      <strong>地图加载失败</strong>
      <p>{{ errorMessage }}</p>
      <el-button
        v-if="errorCode !== 'MISSING_KEY'"
        type="primary"
        round
        size="small"
        @click="initMap"
      >
        重新加载地图
      </el-button>
      <small v-else class="amap-overlay__hint">
        配置 Key 后刷新页面即可加载地图。
      </small>
    </div>

    <div v-if="mapReady" class="amap-controls">
      <el-tooltip content="放大" placement="left">
        <el-button circle size="small" :icon="Plus" aria-label="放大地图" @click="zoomIn" />
      </el-tooltip>
      <el-tooltip content="缩小" placement="left">
        <el-button circle size="small" :icon="Minus" aria-label="缩小地图" @click="zoomOut" />
      </el-tooltip>
      <el-tooltip content="查看全部点位" placement="left">
        <el-button circle size="small" :icon="Aim" aria-label="查看全部点位" @click="resetView" />
      </el-tooltip>
    </div>
  </div>
</template>

<style scoped>
.amap-shell {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
  overflow: hidden;
  border-radius: inherit;
  background: #e8f0e8;
}

.amap-container {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.amap-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 5px;
  width: min(320px, 82%);
  padding: 22px;
  border: 1px solid rgba(220, 234, 248, 0.95);
  border-radius: 17px;
  text-align: center;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 44px rgba(42, 84, 132, 0.15);
  transform: translate(-50%, -50%);
  backdrop-filter: blur(12px);
}

.amap-overlay strong {
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
}

.amap-overlay p {
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.7;
}

.amap-overlay--error {
  border-color: #f2e2d8;
}

.amap-overlay__icon {
  display: grid;
  width: 42px;
  height: 42px;
  margin-bottom: 4px;
  border-radius: 13px;
  color: #c78524;
  font-size: 20px;
  place-items: center;
  background: #fff7e9;
}

.amap-overlay .el-button {
  margin-top: 7px;
}

.amap-overlay__hint {
  margin-top: 4px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.6;
}

.amap-spinner {
  width: 28px;
  height: 28px;
  margin-bottom: 5px;
  border: 2px solid #dceafc;
  border-top-color: var(--ta-primary);
  border-radius: 50%;
  animation: amap-spin 0.9s linear infinite;
}

.amap-controls {
  position: absolute;
  right: 14px;
  bottom: 20px;
  z-index: 4;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.amap-controls :deep(.el-button) {
  color: #5a7392;
  border-color: #dce8f6;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 6px 16px rgba(40, 86, 146, 0.13);
}

.amap-controls :deep(.el-button:hover) {
  color: var(--ta-primary);
}

@keyframes amap-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
