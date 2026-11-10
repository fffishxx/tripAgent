<script setup lang="ts">
import {
  computed,
  nextTick,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Close,
  Collection,
  Flag,
  Location,
  Lock,
  MapLocation,
  Position,
  Search,
  Star,
  StarFilled,
  Suitcase,
  WarningFilled,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import AddToTripDialog from '@/components/map/AddToTripDialog.vue'
import AmapCanvas from '@/components/map/AmapCanvas.vue'
import FavoriteDrawer from '@/components/map/FavoriteDrawer.vue'
import { planRoute, searchPois } from '@/api/amap'
import {
  addFavorite,
  getAllFavorites,
  removeFavorite,
} from '@/api/favorites'
import { useAuthStore } from '@/stores/auth'
import type {
  AmapPoi,
  AmapRouteMode,
} from '@/types/amap'
import type { FavoritePlace } from '@/types/favorite'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

interface CategoryOption {
  key: string
  label: string
  types: string
}

const categories: CategoryOption[] = [
  { key: 'all', label: '全部', types: '' },
  { key: 'attraction', label: '景点', types: '110000' },
  { key: 'food', label: '美食', types: '050000' },
  { key: 'hotel', label: '酒店', types: '100000' },
  { key: 'shopping', label: '购物', types: '060000' },
  { key: 'leisure', label: '休闲', types: '080000' },
]

const quickSearches = [
  { label: '杭州西湖', keyword: '西湖', city: '杭州' },
  { label: '北京故宫', keyword: '故宫', city: '北京' },
  { label: '成都宽窄巷子', keyword: '宽窄巷子', city: '成都' },
  { label: '上海外滩', keyword: '外滩', city: '上海' },
]

const keyword = ref('')
const city = ref('')
const activeCategory = ref('all')
const results = ref<AmapPoi[]>([])
const searchTotal = ref(0)
const searchLoading = ref(false)
const searchError = ref('')
const hasSearched = ref(false)
const selectedPoi = ref<AmapPoi | null>(null)
const failedPhotos = reactive(new Set<string>())
const loadedPhotoUrl = ref('')

const favorites = ref<FavoritePlace[]>([])
const favoritesLoading = ref(false)
const favoritesError = ref('')
const favoritesDrawerVisible = ref(false)
const removingFavoriteId = ref<string | null>(null)
const favoriteActionLoading = ref(false)
const addTripDialogVisible = ref(false)

const routeStart = ref<AmapPoi | null>(null)
const routeEnd = ref<AmapPoi | null>(null)
const routeMode = ref<AmapRouteMode>('driving')
const routeLoading = ref(false)
const routeError = ref('')
const routePath = ref<Array<[number, number]>>([])
const routeDistanceM = ref(0)
const routeDurationSec = ref(0)

let searchRequestId = 0
let routeRequestId = 0

const activeCategoryTypes = computed(
  () => categories.find((item) => item.key === activeCategory.value)?.types ?? '',
)

const selectedFavorite = computed(() =>
  selectedPoi.value ? findFavoriteForPoi(selectedPoi.value) : null,
)

const isRouteStart = computed(
  () =>
    Boolean(routeStart.value && selectedPoi.value) &&
    routeStart.value?.id === selectedPoi.value?.id,
)

const isRouteEnd = computed(
  () =>
    Boolean(routeEnd.value && selectedPoi.value) &&
    routeEnd.value?.id === selectedPoi.value?.id,
)

const hasRouteEndpoint = computed(
  () => Boolean(routeStart.value || routeEnd.value),
)

const bothRouteEndpoints = computed(
  () => Boolean(routeStart.value && routeEnd.value),
)

const routeModeLabel = computed(() =>
  routeMode.value === 'walking' ? '步行' : '驾车',
)

const routeSummaryText = computed(() => {
  if (routeLoading.value) {
    return '正在规划路线…'
  }
  if (routeError.value) {
    return routeError.value
  }
  if (routeDistanceM.value > 0) {
    return `${routeModeLabel.value} ${formatDistance(
      routeDistanceM.value,
    )} · ${formatDuration(routeDurationSec.value)}`
  }
  return '选择两个不同景点后生成路线'
})

const searchHint = computed(() => {
  if (searchLoading.value) {
    return '正在搜索…'
  }
  if (!hasSearched.value) {
    return '输入关键词开始探索'
  }
  if (results.value.length === 0) {
    return '没有找到匹配地点'
  }
  return `共 ${searchTotal.value} 个结果，地图显示前 ${results.value.length} 个标记`
})

function formatDistance(meters: number): string {
  if (meters < 1000) {
    return `${Math.round(meters)} 米`
  }
  return `${(meters / 1000).toFixed(1)} 公里`
}

function formatDuration(seconds: number): string {
  const minutes = Math.max(1, Math.ceil(seconds / 60))
  if (minutes < 60) {
    return `约 ${minutes} 分钟`
  }
  const hours = Math.floor(minutes / 60)
  const remaining = minutes % 60
  return remaining > 0
    ? `约 ${hours} 小时 ${remaining} 分钟`
    : `约 ${hours} 小时`
}

function distanceMeters(
  fromLongitude: number,
  fromLatitude: number,
  toLongitude: number,
  toLatitude: number,
): number {
  const earthRadius = 6_371_000
  const toRad = (value: number): number => (value * Math.PI) / 180
  const deltaLat = toRad(toLatitude - fromLatitude)
  const deltaLng = toRad(toLongitude - fromLongitude)
  const a =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(toRad(fromLatitude)) *
      Math.cos(toRad(toLatitude)) *
      Math.sin(deltaLng / 2) ** 2
  return 2 * earthRadius * Math.asin(Math.sqrt(a))
}

function findFavoriteForPoi(poi: AmapPoi): FavoritePlace | null {
  const externalMatch = favorites.value.find(
    (favorite) =>
      favorite.externalPoiId && favorite.externalPoiId === poi.id,
  )
  if (externalMatch) {
    return externalMatch
  }

  return (
    favorites.value.find((favorite) => {
      if (favorite.name !== poi.name) {
        return false
      }
      if (
        favorite.longitude === null ||
        favorite.longitude === undefined ||
        favorite.latitude === null ||
        favorite.latitude === undefined
      ) {
        return false
      }
      return (
        distanceMeters(
          poi.longitude,
          poi.latitude,
          favorite.longitude,
          favorite.latitude,
        ) <= 150
      )
    }) ?? null
  )
}

function getPhotoUrl(poi: AmapPoi | null): string {
  if (!poi?.photoUrl || failedPhotos.has(poi.photoUrl)) {
    return ''
  }
  return poi.photoUrl
}

function markPhotoFailed(url: string): void {
  if (url) {
    failedPhotos.add(url)
  }
}

const pendingPhotoUrl = computed(() => {
  const url = getPhotoUrl(selectedPoi.value)
  return url && loadedPhotoUrl.value !== url ? url : ''
})

const selectedPhotoUrl = computed(() => {
  const url = getPhotoUrl(selectedPoi.value)
  return url && loadedPhotoUrl.value === url ? url : ''
})

function handlePhotoLoad(url: string): void {
  if (getPhotoUrl(selectedPoi.value) === url) {
    loadedPhotoUrl.value = url
  }
}

watch(selectedPoi, () => {
  loadedPhotoUrl.value = ''
})

async function performSearch(): Promise<void> {
  const trimmedKeyword = keyword.value.trim()
  if (!trimmedKeyword) {
    ElMessage.warning('请输入景点或地点关键词')
    return
  }

  const requestId = ++searchRequestId
  searchLoading.value = true
  searchError.value = ''
  hasSearched.value = true
  selectedPoi.value = null
  clearRoute()

  try {
    const result = await searchPois({
      keywords: trimmedKeyword,
      city: city.value,
      types: activeCategoryTypes.value || undefined,
      page: 1,
      pageSize: 20,
    })
    if (requestId !== searchRequestId) {
      return
    }
    results.value = result.pois
    searchTotal.value = result.total
  } catch (error) {
    if (requestId !== searchRequestId) {
      return
    }
    results.value = []
    searchTotal.value = 0
    searchError.value =
      error instanceof Error ? error.message : 'POI 搜索失败，请稍后重试'
  } finally {
    if (requestId === searchRequestId) {
      searchLoading.value = false
    }
  }
}

function selectCategory(category: CategoryOption): void {
  if (activeCategory.value === category.key) {
    return
  }
  activeCategory.value = category.key
  if (hasSearched.value && keyword.value.trim()) {
    void performSearch()
  }
}

function runQuickSearch(item: { keyword: string; city: string }): void {
  keyword.value = item.keyword
  city.value = item.city
  activeCategory.value = 'all'
  void performSearch()
}

function selectPoi(poi: AmapPoi): void {
  selectedPoi.value = poi
  if (window.innerWidth <= 960) {
    void nextTick(() => {
      document
        .querySelector('.map-sidebar')
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }
}

function clearSelection(): void {
  selectedPoi.value = null
}

async function requireLogin(action: string): Promise<boolean> {
  if (auth.isLoggedIn) {
    return true
  }

  try {
    await ElMessageBox.confirm(
      `登录后才能${action}，是否现在登录？`,
      '需要登录',
      {
        confirmButtonText: '去登录',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    void router.push({
      name: 'login',
      query: {
        redirect: route.fullPath,
      },
    })
  } catch {
    // 用户取消登录。
  }
  return false
}

async function loadFavorites(): Promise<void> {
  if (!auth.isLoggedIn) {
    favorites.value = []
    favoritesError.value = ''
    return
  }

  favoritesLoading.value = true
  favoritesError.value = ''
  try {
    favorites.value = await getAllFavorites()
  } catch (error) {
    favoritesError.value =
      error instanceof Error ? error.message : '收藏加载失败'
  } finally {
    favoritesLoading.value = false
  }
}

function openFavorites(): void {
  favoritesDrawerVisible.value = true
  if (auth.isLoggedIn) {
    void loadFavorites()
  }
}

function goToLogin(): void {
  favoritesDrawerVisible.value = false
  void router.push({
    name: 'login',
    query: {
      redirect: route.fullPath,
    },
  })
}

async function toggleFavorite(): Promise<void> {
  const poi = selectedPoi.value
  if (!poi || favoriteActionLoading.value) {
    return
  }
  if (!(await requireLogin('收藏景点'))) {
    return
  }

  favoriteActionLoading.value = true
  try {
    const existing = findFavoriteForPoi(poi)
    if (existing) {
      await removeFavorite(existing.id)
      favorites.value = favorites.value.filter(
        (favorite) => favorite.id !== existing.id,
      )
      ElMessage.success('已取消收藏')
    } else {
      const added = await addFavorite({
        externalPoiId: poi.id,
        name: poi.name,
        city: poi.city || poi.district,
        address: poi.address,
        longitude: poi.longitude,
        latitude: poi.latitude,
        category: poi.typeLabel,
        typecode: poi.typecode,
        source: 'SEARCH',
      })
      const index = favorites.value.findIndex(
        (favorite) =>
          favorite.id === added.id ||
          (favorite.externalPoiId &&
            favorite.externalPoiId === added.externalPoiId),
      )
      if (index === -1) {
        favorites.value = [added, ...favorites.value]
      } else {
        favorites.value.splice(index, 1, added)
      }
      ElMessage.success(added.duplicate ? '该景点已在收藏中' : '已收藏景点')
    }
  } catch {
    // 错误提示由请求层统一处理。
  } finally {
    favoriteActionLoading.value = false
  }
}

async function removeFavoriteFromDrawer(
  favorite: FavoritePlace,
): Promise<void> {
  if (removingFavoriteId.value) {
    return
  }
  removingFavoriteId.value = favorite.id
  try {
    await removeFavorite(favorite.id)
    favorites.value = favorites.value.filter(
      (item) => item.id !== favorite.id,
    )
    ElMessage.success('已取消收藏')
  } catch {
    // 错误提示由请求层统一处理。
  } finally {
    removingFavoriteId.value = null
  }
}

async function openAddToTrip(): Promise<void> {
  if (!selectedPoi.value) {
    return
  }
  if (!(await requireLogin('将景点加入行程'))) {
    return
  }
  addTripDialogVisible.value = true
}

function clearRoute(): void {
  routeRequestId += 1
  routeStart.value = null
  routeEnd.value = null
  routePath.value = []
  routeDistanceM.value = 0
  routeDurationSec.value = 0
  routeLoading.value = false
  routeError.value = ''
}

function setRouteStart(poi: AmapPoi | null): void {
  if (!poi) {
    return
  }
  if (routeEnd.value?.id === poi.id) {
    ElMessage.warning('起点和终点不能是同一个地点')
    return
  }
  routeStart.value = routeStart.value?.id === poi.id ? null : poi
  void planRouteIfReady()
}

function setRouteEnd(poi: AmapPoi | null): void {
  if (!poi) {
    return
  }
  if (routeStart.value?.id === poi.id) {
    ElMessage.warning('起点和终点不能是同一个地点')
    return
  }
  routeEnd.value = routeEnd.value?.id === poi.id ? null : poi
  void planRouteIfReady()
}

async function planRouteIfReady(): Promise<void> {
  const start = routeStart.value
  const end = routeEnd.value
  routePath.value = []
  routeDistanceM.value = 0
  routeDurationSec.value = 0
  routeError.value = ''

  if (!start || !end) {
    routeLoading.value = false
    return
  }

  const requestId = ++routeRequestId
  routeLoading.value = true
  try {
    const result = await planRoute({
      mode: routeMode.value,
      origin: [start.longitude, start.latitude],
      destination: [end.longitude, end.latitude],
    })
    if (requestId !== routeRequestId) {
      return
    }
    routePath.value = result.path
    routeDistanceM.value = result.distanceM
    routeDurationSec.value = result.durationSec
  } catch (error) {
    if (requestId !== routeRequestId) {
      return
    }
    routeError.value =
      error instanceof Error ? error.message : '路线规划失败，请稍后重试'
  } finally {
    if (requestId === routeRequestId) {
      routeLoading.value = false
    }
  }
}

function switchRouteMode(mode: AmapRouteMode): void {
  if (routeMode.value === mode) {
    return
  }
  routeMode.value = mode
  if (bothRouteEndpoints.value) {
    void planRouteIfReady()
  }
}

function resetSearch(): void {
  keyword.value = ''
  results.value = []
  searchTotal.value = 0
  hasSearched.value = false
  searchError.value = ''
  selectedPoi.value = null
  clearRoute()
}

onMounted(() => {
  if (auth.isLoggedIn) {
    void loadFavorites()
  }
})
</script>

<template>
  <div class="page map-page">
    <PageHeader
      title="地图探索"
      description="搜索目的地与景点，收藏灵感，或把新景点加入已有行程。"
      :icon="Location"
    >
      <template #actions>
        <el-button :icon="Collection" @click="openFavorites">
          收藏列表
          <span
            v-if="auth.isLoggedIn && favorites.length"
            class="favorites-count"
          >
            {{ favorites.length }}
          </span>
        </el-button>
      </template>
    </PageHeader>

    <section class="map-toolbar surface-card">
      <div class="map-search-row">
        <el-input
          v-model="city"
          clearable
          placeholder="城市（可选）"
          class="map-city-input"
        />
        <el-input
          v-model="keyword"
          clearable
          :prefix-icon="Search"
          placeholder="搜索景点、地点、商圈…"
          class="map-keyword-input"
          @keyup.enter="performSearch"
        />
        <el-button
          type="primary"
          :icon="Search"
          :loading="searchLoading"
          @click="performSearch"
        >
          搜索
        </el-button>
      </div>

      <div class="map-toolbar__footer">
        <div class="filter-chips" aria-label="地点分类">
          <button
            v-for="category in categories"
            :key="category.key"
            type="button"
            class="filter-chip"
            :class="{
              'filter-chip--active': activeCategory === category.key,
            }"
            @click="selectCategory(category)"
          >
            {{ category.label }}
          </button>
        </div>
        <span class="map-toolbar__source">
          POI 与路线数据来自高德
        </span>
      </div>
    </section>

    <div class="map-layout">
      <aside class="map-sidebar surface-card">
        <template v-if="selectedPoi">
          <header class="map-side-head">
            <button type="button" class="map-back" @click="clearSelection">
              <el-icon><ArrowLeft /></el-icon>
              返回结果
            </button>
            <span class="map-side-head__label">景点详情</span>
          </header>

          <div class="poi-detail">
            <img
              v-if="selectedPhotoUrl"
              class="poi-detail__photo"
              :src="selectedPhotoUrl"
              :alt="selectedPoi.name"
              referrerpolicy="no-referrer"
            />
            <img
              v-else-if="pendingPhotoUrl"
              class="poi-detail__preload"
              :src="pendingPhotoUrl"
              alt=""
              aria-hidden="true"
              referrerpolicy="no-referrer"
              @load="handlePhotoLoad(pendingPhotoUrl)"
              @error="markPhotoFailed(pendingPhotoUrl)"
            />

            <div class="poi-detail__meta">
              <span class="poi-detail__type">
                {{ selectedPoi.typeLabel }}
              </span>
              <span v-if="selectedPoi.rating" class="poi-detail__rating">
                <el-icon><StarFilled /></el-icon>
                {{ selectedPoi.rating.toFixed(1) }}
              </span>
              <span v-if="selectedPoi.city" class="poi-detail__city">
                {{ selectedPoi.city }}
                <template v-if="selectedPoi.district">
                  · {{ selectedPoi.district }}
                </template>
              </span>
            </div>

            <h2 class="poi-detail__name">{{ selectedPoi.name }}</h2>

            <p v-if="selectedPoi.address" class="poi-detail__line">
              <el-icon><MapLocation /></el-icon>
              {{ selectedPoi.address }}
            </p>
            <p v-if="selectedPoi.tel" class="poi-detail__line">
              <el-icon><Location /></el-icon>
              {{ selectedPoi.tel }}
            </p>

            <div class="poi-detail__actions">
              <el-button
                :icon="selectedFavorite ? StarFilled : Star"
                :loading="favoriteActionLoading"
                class="poi-detail__favorite"
                @click="toggleFavorite"
              >
                {{ selectedFavorite ? '取消收藏' : '收藏景点' }}
              </el-button>
              <el-button
                type="primary"
                :icon="Suitcase"
                @click="openAddToTrip"
              >
                加入行程
              </el-button>
            </div>

            <div class="poi-route-actions">
              <el-button
                plain
                :type="isRouteStart ? 'primary' : 'default'"
                :icon="Position"
                :disabled="isRouteStart"
                @click="setRouteStart(selectedPoi)"
              >
                {{ isRouteStart ? '起点已选' : '设为起点' }}
              </el-button>
              <el-button
                plain
                :type="isRouteEnd ? 'primary' : 'default'"
                :icon="Flag"
                :disabled="isRouteEnd"
                @click="setRouteEnd(selectedPoi)"
              >
                {{ isRouteEnd ? '终点已选' : '设为终点' }}
              </el-button>
            </div>

            <p class="poi-detail__tip">
              点击地图上的数字标记查看其他景点；在详情中设置起点和终点即可规划路线。
            </p>
          </div>
        </template>

        <template v-else>
          <header class="map-side-head">
            <div>
              <h2>
                {{ hasSearched ? '搜索结果' : '从哪里开始？' }}
              </h2>
              <p>{{ searchHint }}</p>
            </div>
            <el-button
              v-if="hasSearched"
              text
              size="small"
              @click="resetSearch"
            >
              清空
            </el-button>
          </header>

          <div v-if="searchLoading" class="map-side-loading">
            <el-skeleton :rows="5" animated />
          </div>

          <div v-else-if="searchError" class="map-side-state">
            <span class="map-side-state__icon map-side-state__icon--warning">
              <el-icon><WarningFilled /></el-icon>
            </span>
            <strong>搜索失败</strong>
            <p>{{ searchError }}</p>
            <el-button round size="small" @click="performSearch">
              重新搜索
            </el-button>
          </div>

          <div v-else-if="!hasSearched" class="map-welcome">
            <div class="map-welcome__hero">
              <span class="map-welcome__icon">
                <el-icon><MapLocation /></el-icon>
              </span>
              <strong>探索附近与远方</strong>
              <p>输入城市和关键词，找到心动景点。收藏喜欢的去处，或把景点加入已有行程。</p>
            </div>

            <div class="map-welcome__suggestions">
              <span class="map-welcome__label">试试这些搜索</span>
              <button
                v-for="suggestion in quickSearches"
                :key="suggestion.label"
                type="button"
                class="suggestion-chip"
                @click="runQuickSearch(suggestion)"
              >
                {{ suggestion.label }}
              </button>
            </div>

            <div v-if="!auth.isLoggedIn" class="map-login-note">
              <el-icon><Lock /></el-icon>
              <span>登录后可以收藏景点、查看收藏列表并加入行程。</span>
              <button type="button" class="text-button" @click="goToLogin">
                去登录
              </button>
            </div>
          </div>

          <div v-else-if="results.length === 0" class="map-side-state">
            <span class="map-side-state__icon">
              <el-icon><Search /></el-icon>
            </span>
            <strong>没有找到匹配地点</strong>
            <p>换个关键词、放宽城市范围，或者切换一个兴趣分类再试试。</p>
            <el-button round size="small" @click="performSearch">
              重试当前搜索
            </el-button>
          </div>

          <div v-else class="poi-results">
            <button
              v-for="(poi, index) in results"
              :key="poi.id"
              type="button"
              class="poi-result"
              @click="selectPoi(poi)"
            >
              <span class="poi-result__index">{{ index + 1 }}</span>
              <span class="poi-result__main">
                <span class="poi-result__name-row">
                  <strong>{{ poi.name }}</strong>
                  <span v-if="poi.rating" class="poi-result__rating">
                    <el-icon><StarFilled /></el-icon>
                    {{ poi.rating.toFixed(1) }}
                  </span>
                </span>
                <small>
                  {{ poi.typeLabel }}
                  <template v-if="poi.district"> · {{ poi.district }}</template>
                  <template v-else-if="poi.city"> · {{ poi.city }}</template>
                </small>
                <span class="poi-result__address">
                  {{ poi.address || '暂无详细地址' }}
                </span>
              </span>
              <el-icon class="poi-result__arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </template>
      </aside>

      <div class="map-stage surface-card">
        <AmapCanvas
          :pois="results"
          :selected-poi-id="selectedPoi?.id ?? null"
          :route-path="routePath"
          :route-start="routeStart"
          :route-end="routeEnd"
          @poi-click="selectPoi"
        />

        <div v-if="hasRouteEndpoint" class="route-bar">
          <div class="route-bar__head">
            <div class="route-bar__points">
              <span v-if="routeStart" class="route-bar__point">
                <em>起</em>
                {{ routeStart.name }}
              </span>
              <el-icon v-if="bothRouteEndpoints" class="route-bar__arrow">
                <ArrowRight />
              </el-icon>
              <span v-if="routeEnd" class="route-bar__point">
                <em>终</em>
                {{ routeEnd.name }}
              </span>
              <span v-if="!bothRouteEndpoints" class="route-bar__hint">
                请再选择一个{{ routeStart ? '终点' : '起点' }}
              </span>
            </div>
            <button
              type="button"
              class="route-bar__clear"
              aria-label="清除路线"
              @click="clearRoute"
            >
              <el-icon><Close /></el-icon>
            </button>
          </div>

          <div v-if="bothRouteEndpoints" class="route-bar__body">
            <div class="route-mode">
              <button
                type="button"
                :class="{ 'route-mode__active': routeMode === 'driving' }"
                @click="switchRouteMode('driving')"
              >
                驾车
              </button>
              <button
                type="button"
                :class="{ 'route-mode__active': routeMode === 'walking' }"
                @click="switchRouteMode('walking')"
              >
                步行
              </button>
            </div>
            <span
              class="route-bar__summary"
              :class="{ 'route-bar__summary--error': routeError }"
            >
              {{ routeSummaryText }}
            </span>
            <el-button
              v-if="routeError && !routeLoading"
              text
              size="small"
              @click="planRouteIfReady"
            >
              重试
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <FavoriteDrawer
      v-model="favoritesDrawerVisible"
      :favorites="favorites"
      :loading="favoritesLoading"
      :error="favoritesError"
      :logged-in="auth.isLoggedIn"
      :removing-id="removingFavoriteId"
      @refresh="loadFavorites"
      @remove="removeFavoriteFromDrawer"
      @login="goToLogin"
    />

    <AddToTripDialog
      v-model="addTripDialogVisible"
      :poi="selectedPoi"
    />
  </div>
</template>

<style scoped>
.map-page {
  max-width: 1360px;
  margin: 0 auto;
}

.favorites-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 17px;
  height: 17px;
  margin-left: 5px;
  padding: 0 5px;
  border-radius: 999px;
  color: var(--ta-primary);
  font-size: 9px;
  background: #eaf4ff;
}

.map-toolbar {
  padding: 16px 18px;
}

.map-search-row {
  display: flex;
  gap: 9px;
  align-items: center;
}

.map-city-input {
  flex: 0 1 150px;
}

.map-keyword-input {
  flex: 1;
}

.map-toolbar__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.map-toolbar__source {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
}

.map-layout {
  display: grid;
  grid-template-columns: 356px minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
}

.map-sidebar {
  scroll-margin-top: 84px;
  display: flex;
  flex-direction: column;
  height: 650px;
  overflow-y: auto;
  padding: 18px;
}

.map-side-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.map-side-head h2 {
  color: var(--ta-ink);
  font-size: 15px;
  font-weight: 600;
}

.map-side-head p {
  margin-top: 2px;
  color: var(--ta-ink-muted);
  font-size: 10px;
  line-height: 1.6;
}

.map-side-head__label {
  flex: 0 0 auto;
  padding-top: 2px;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.map-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  color: var(--ta-primary);
  font-size: 10.5px;
  background: transparent;
  cursor: pointer;
}

.map-back:hover {
  color: var(--ta-primary-deep);
}

.map-side-loading {
  padding: 4px 2px;
}

.map-side-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 5px;
  padding: 44px 14px;
  text-align: center;
}

.map-side-state__icon {
  display: grid;
  width: 44px;
  height: 44px;
  margin-bottom: 5px;
  border-radius: 14px;
  color: var(--ta-primary);
  font-size: 21px;
  place-items: center;
  background: #eaf4ff;
}

.map-side-state__icon--warning {
  color: #c9821d;
  background: #fff6e8;
}

.map-side-state strong {
  color: var(--ta-ink);
  font-size: 12.5px;
  font-weight: 600;
}

.map-side-state p {
  max-width: 280px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.7;
}

.map-side-state .el-button {
  margin-top: 5px;
}

.map-welcome__hero {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 22px 12px 18px;
  border: 1px solid #eaf2fb;
  border-radius: 14px;
  text-align: center;
  background:
    radial-gradient(circle at 50% 0%, rgba(205, 229, 255, 0.55), transparent 62%),
    #fbfdff;
}

.map-welcome__icon {
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 9px;
  border-radius: 15px;
  color: var(--ta-primary);
  font-size: 23px;
  place-items: center;
  background: linear-gradient(145deg, #eef7ff, #d9eaff);
  box-shadow: 0 10px 22px rgba(47, 126, 247, 0.13);
}

.map-welcome__hero strong {
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
}

.map-welcome__hero p {
  max-width: 280px;
  margin-top: 4px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.75;
}

.map-welcome__suggestions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.map-welcome__label {
  flex-basis: 100%;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
}

.suggestion-chip {
  padding: 5px 9px;
  border: 1px solid #e4effb;
  border-radius: 8px;
  color: var(--ta-ink-secondary);
  font-size: 10px;
  background: #fbfdff;
  cursor: pointer;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.suggestion-chip:hover {
  color: var(--ta-primary);
  border-color: #c5dbf6;
  background: #f4f9ff;
}

.map-login-note {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding: 9px 10px;
  border: 1px dashed #e8dcc3;
  border-radius: 9px;
  color: #8a6d34;
  font-size: 10px;
  line-height: 1.65;
  background: #fffbf3;
}

.text-button {
  padding: 0;
  border: 0;
  color: var(--ta-primary);
  font-size: inherit;
  background: transparent;
  cursor: pointer;
}

.text-button:hover {
  text-decoration: underline;
}

.poi-results {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.poi-result {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  width: 100%;
  padding: 10px 9px;
  border: 1px solid transparent;
  border-radius: 11px;
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease;
}

.poi-result:hover,
.poi-result--active {
  border-color: #e3effb;
  background: #f8fcff;
}

.poi-result__index {
  display: grid;
  flex: 0 0 auto;
  width: 21px;
  height: 21px;
  margin-top: 1px;
  border-radius: 50%;
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  place-items: center;
  background: linear-gradient(145deg, #5a9dff, #2d72e7);
}

.poi-result__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.poi-result__name-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.poi-result__name-row strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.poi-result__rating {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
  color: #d3921f;
  font-size: 9px;
}

.poi-result__main small {
  overflow: hidden;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.poi-result__address {
  overflow: hidden;
  color: #9aabc0;
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.poi-result__arrow {
  flex: 0 0 auto;
  margin-top: 5px;
  color: #b7c9df;
  font-size: 11px;
}

.poi-detail {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.poi-detail__photo {
  width: 100%;
  height: 170px;
  object-fit: cover;
  border-radius: 13px;
  background: #edf4fc;
}

.poi-detail__preload {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.poi-detail__meta {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.poi-detail__type {
  padding: 2px 7px;
  border-radius: 5px;
  color: var(--ta-primary);
  font-size: 9.5px;
  background: #eaf4ff;
}

.poi-detail__rating {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: #d3921f;
  font-size: 10px;
  font-weight: 500;
}

.poi-detail__city {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
}

.poi-detail__name {
  color: var(--ta-ink-strong);
  font-size: 19px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.poi-detail__line {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.65;
}

.poi-detail__line .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.poi-detail__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
  margin-top: 5px;
}

.poi-detail__actions :deep(.el-button) {
  width: 100%;
  margin-left: 0;
}

.poi-detail__favorite {
  color: #a87818;
  border-color: #ecd9b7;
  background: #fffdf8;
}

.poi-route-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
}

.poi-route-actions :deep(.el-button) {
  width: 100%;
  margin-left: 0;
  font-size: 11px;
}

.poi-detail__tip {
  margin-top: 5px;
  padding: 8px 9px;
  border-radius: 8px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.65;
  background: #f8fbfe;
}

.map-stage {
  position: relative;
  overflow: hidden;
  height: 650px;
  padding: 0;
  border-radius: var(--ta-radius-lg);
}

.route-bar {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 5;
  width: min(480px, calc(100% - 28px));
  padding: 10px 12px;
  border: 1px solid rgba(220, 234, 248, 0.96);
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 12px 30px rgba(41, 87, 145, 0.15);
  backdrop-filter: blur(12px);
}

.route-bar__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.route-bar__points {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  color: var(--ta-ink-secondary);
  font-size: 10.5px;
}

.route-bar__point {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 170px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-bar__point em {
  display: inline-grid;
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  color: #fff;
  font-size: 8px;
  font-style: normal;
  place-items: center;
  background: var(--ta-primary);
}

.route-bar__point:last-child em {
  background: #22a978;
}

.route-bar__arrow {
  color: #a5b9d0;
  font-size: 11px;
}

.route-bar__hint {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
}

.route-bar__clear {
  display: grid;
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 6px;
  color: #98aec6;
  background: transparent;
  cursor: pointer;
  place-items: center;
}

.route-bar__clear:hover {
  color: var(--ta-primary);
  background: #eaf4ff;
}

.route-bar__body {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.route-mode {
  display: inline-flex;
  padding: 2px;
  border-radius: 8px;
  background: #f0f6fd;
}

.route-mode button {
  padding: 3px 8px;
  border: 0;
  border-radius: 6px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  background: transparent;
  cursor: pointer;
}

.route-mode button.route-mode__active {
  color: var(--ta-primary-deep);
  background: #fff;
  box-shadow: 0 2px 6px rgba(42, 92, 158, 0.1);
}

.route-bar__summary {
  color: var(--ta-ink-secondary);
  font-size: 10px;
}

.route-bar__summary--error {
  color: #b46a5a;
}

@media (max-width: 1100px) {
  .map-layout {
    grid-template-columns: 310px minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .map-layout {
    display: flex;
    flex-direction: column;
  }

  .map-stage {
    order: 1;
    height: 460px;
  }

  .map-sidebar {
    order: 2;
    height: auto;
    max-height: none;
  }
}

@media (max-width: 760px) {
  .map-toolbar {
    padding: 14px;
  }

  .map-search-row {
    flex-wrap: wrap;
  }

  .map-city-input {
    flex: 1 1 110px;
  }

  .map-keyword-input {
    flex: 1 1 calc(100% - 120px);
  }

  .map-search-row > .el-button {
    flex: 0 0 auto;
  }

  .map-toolbar__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .map-stage {
    height: 380px;
  }

  .map-sidebar {
    padding: 14px;
  }

  .route-bar {
    top: 10px;
    left: 10px;
    width: calc(100% - 20px);
  }

  .poi-detail__photo {
    height: 150px;
  }
}
</style>
