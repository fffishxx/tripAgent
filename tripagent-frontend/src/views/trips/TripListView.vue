<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  Calendar,
  MagicStick,
  Plus,
  Refresh,
  Search,
  WarningFilled,
} from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import TripCover from '@/components/trips/TripCover.vue'
import { getTrips } from '@/api/trips'
import type { TripSummary } from '@/types/trip'
import { formatDateTime } from '@/utils/format'
import {
  deriveTripStatus,
  formatTripDateRange,
  getSourceLabel,
} from '@/utils/trip'

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 6

const initialPage = Number.parseInt(
  typeof route.query.page === 'string' ? route.query.page : '1',
  10,
)

const trips = ref<TripSummary[]>([])
const total = ref(0)
const pageNum = ref(
  Number.isFinite(initialPage) && initialPage > 0 ? initialPage : 1,
)
const keyword = ref(
  typeof route.query.keyword === 'string' ? route.query.keyword : '',
)
const loading = ref(true)
const loadError = ref('')
let requestSeq = 0

function syncQuery(): void {
  const query: Record<string, string> = {}
  const trimmed = keyword.value.trim()
  if (trimmed) {
    query.keyword = trimmed
  }
  if (pageNum.value > 1) {
    query.page = String(pageNum.value)
  }
  void router.replace({ name: 'trips', query })
}

const hasKeyword = computed(() => Boolean(keyword.value.trim()))

const resultLabel = computed(() =>
  hasKeyword.value ? `找到 ${total.value} 个行程` : `共 ${total.value} 个行程`,
)

async function fetchTrips(resetPage = false): Promise<void> {
  if (resetPage) {
    pageNum.value = 1
  }

  const seq = ++requestSeq
  loading.value = true
  loadError.value = ''

  try {
    const page = await getTrips({
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
      keyword: keyword.value,
    })
    if (seq !== requestSeq) {
      return
    }

    trips.value = page.records ?? []
    total.value = page.total ?? 0

    const lastPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
    if (pageNum.value > lastPage) {
      pageNum.value = lastPage
      syncQuery()
      void fetchTrips()
    }
  } catch (error) {
    if (seq !== requestSeq) {
      return
    }
    loadError.value =
      error instanceof Error ? error.message : '行程列表加载失败'
    trips.value = []
    total.value = 0
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

onMounted(() => {
  void fetchTrips()
})

function handleSearch(): void {
  void fetchTrips(true)
  syncQuery()
}

function clearSearch(): void {
  keyword.value = ''
  void fetchTrips(true)
  syncQuery()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  syncQuery()
  void fetchTrips()
}

function openTrip(trip: TripSummary): void {
  void router.push({
    name: 'trip-detail',
    params: {
      tripId: trip.id,
    },
  })
}

function goCreate(): void {
  void router.push({ name: 'trip-create' })
}

function goChat(): void {
  void router.push({ name: 'chat-home' })
}

function getStatus(trip: TripSummary) {
  return deriveTripStatus(trip.startDate, trip.endDate)
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="我的行程"
      description="管理你的旅行计划，随时查看每一段旅程。"
      :icon="Calendar"
    >
      <template #actions>
        <el-button text :icon="MagicStick" @click="goChat">
          让 AI 规划
        </el-button>
        <el-button type="primary" :icon="Plus" @click="goCreate">
          创建行程
        </el-button>
      </template>
    </PageHeader>

    <section class="trips-card surface-card">
      <div class="trips-toolbar">
        <div class="trips-toolbar__search">
          <el-input
            v-model="keyword"
            clearable
            :prefix-icon="Search"
            placeholder="搜索行程标题或目的地…"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button :icon="Search" @click="handleSearch">搜索</el-button>
        </div>

        <div class="trips-toolbar__meta">
          <span v-if="!loading && !loadError">{{ resultLabel }}</span>
          <span class="trips-toolbar__sort">按最近更新排序</span>
          <el-tooltip content="刷新行程列表" placement="top">
            <el-button
              circle
              :icon="Refresh"
              :loading="loading"
              aria-label="刷新行程列表"
              @click="fetchTrips()"
            />
          </el-tooltip>
        </div>
      </div>

      <div v-if="loading" class="trips-loading">
        <div v-for="index in 3" :key="index" class="trip-skeleton">
          <el-skeleton animated>
            <template #template>
              <div class="trip-skeleton__row">
                <el-skeleton-item variant="image" class="trip-skeleton__cover" />
                <div class="trip-skeleton__body">
                  <el-skeleton-item variant="h3" style="width: 38%" />
                  <el-skeleton-item variant="text" style="width: 62%" />
                  <el-skeleton-item variant="text" style="width: 28%" />
                </div>
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>

      <div v-else-if="loadError" class="trips-state">
        <span class="trips-state__icon trips-state__icon--warning">
          <el-icon><WarningFilled /></el-icon>
        </span>
        <strong>行程列表加载失败</strong>
        <p>{{ loadError }}</p>
        <el-button type="primary" round @click="fetchTrips()">
          重新加载
        </el-button>
      </div>

      <div v-else-if="trips.length === 0 && !hasKeyword">
        <EmptyState
          :icon="Calendar"
          title="还没有行程"
          description="让 TripAgent 为你规划一段旅程，保存后会出现在这里；也可以手动创建一条行程。"
        >
          <el-button type="primary" round :icon="Plus" @click="goCreate">
            创建行程
          </el-button>
          <el-button round :icon="MagicStick" @click="goChat">
            让 AI 规划
          </el-button>
        </EmptyState>
      </div>

      <div v-else-if="trips.length === 0 && hasKeyword">
        <EmptyState
          :icon="Search"
          title="没有找到匹配的行程"
          :description="`没有标题或目的地包含“${keyword.trim()}”的行程，试试其他关键词。`"
        >
          <el-button round @click="clearSearch">清除搜索</el-button>
        </EmptyState>
      </div>

      <template v-else>
        <div class="trip-list">
          <button
            v-for="trip in trips"
            :key="trip.id"
            type="button"
            class="trip-row"
            :aria-label="`查看行程：${trip.title}`"
            @click="openTrip(trip)"
          >
            <TripCover :destination="trip.destination" />

            <span class="trip-row__content">
              <span class="trip-row__title-line">
                <strong>{{ trip.title }}</strong>
                <span
                  class="trip-status"
                  :class="`trip-status--${getStatus(trip).key}`"
                >
                  {{ getStatus(trip).label }}
                </span>
              </span>

              <span class="trip-row__dates">
                {{ formatTripDateRange(trip.startDate, trip.endDate) }}
                <i>·</i>
                {{ trip.daysCount }} 天
                <i>·</i>
                {{ trip.destination }}
              </span>

              <span class="trip-row__footer">
                <span class="trip-source">
                  {{ getSourceLabel(trip.source) }}
                </span>
                <span>更新于 {{ formatDateTime(trip.updateTime) }}</span>
              </span>
            </span>

            <span class="trip-row__arrow">
              <el-icon><ArrowRight /></el-icon>
            </span>
          </button>
        </div>

        <div v-if="total > PAGE_SIZE" class="trip-pagination">
          <el-pagination
            :current-page="pageNum"
            :page-size="PAGE_SIZE"
            :total="total"
            background
            layout="prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.trips-card {
  overflow: hidden;
}

.trips-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 17px 22px;
  border-bottom: 1px solid #eef5fd;
}

.trips-toolbar__search {
  display: flex;
  flex: 1 1 300px;
  gap: 8px;
  max-width: 460px;
}

.trips-toolbar__search :deep(.el-input) {
  flex: 1;
}

.trips-toolbar__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.trips-toolbar__sort {
  padding-left: 12px;
  border-left: 1px solid #e8f0fa;
}

.trips-loading {
  padding: 18px 22px 24px;
}

.trip-skeleton + .trip-skeleton {
  border-top: 1px dashed #edf4fb;
}

.trip-skeleton__row {
  display: flex;
  gap: 16px;
  padding: 14px 0;
}

.trip-skeleton__cover {
  flex: 0 0 auto;
  width: 156px;
  height: 108px;
  border-radius: 15px;
}

.trip-skeleton__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 11px;
  padding-top: 8px;
}

.trips-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  padding: 56px 24px;
  text-align: center;
}

.trips-state__icon {
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 6px;
  border-radius: 15px;
  font-size: 22px;
  place-items: center;
}

.trips-state__icon--warning {
  color: #c9821d;
  background: #fff6e8;
}

.trips-state strong {
  color: var(--ta-ink);
  font-size: 14px;
  font-weight: 600;
}

.trips-state p {
  max-width: 420px;
  color: var(--ta-ink-muted);
  font-size: 12px;
  line-height: 1.7;
}

.trips-state .el-button {
  margin-top: 8px;
}

.trip-list {
  padding: 6px 14px;
}

.trip-row {
  display: flex;
  align-items: center;
  gap: 17px;
  width: 100%;
  padding: 13px;
  border: 1px solid transparent;
  border-radius: 16px;
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.trip-row + .trip-row {
  margin-top: 2px;
}

.trip-row:hover {
  border-color: #e4effb;
  background: #f9fcff;
  box-shadow: 0 10px 24px rgba(47, 100, 170, 0.07);
  transform: translateY(-1px);
}

.trip-row__content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.trip-row__title-line {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

.trip-row__title-line strong {
  overflow: hidden;
  color: var(--ta-ink-strong);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-status {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 9.5px;
  font-weight: 500;
}

.trip-status--upcoming {
  color: #2a6cc7;
  background: #eaf4ff;
}

.trip-status--ongoing {
  color: #a97415;
  background: #fff4e1;
}

.trip-status--completed {
  color: #1e835c;
  background: #e9f8f1;
}

.trip-row__dates {
  color: var(--ta-ink-secondary);
  font-size: 12px;
}

.trip-row__dates i {
  margin: 0 5px;
  color: #c4d3e5;
  font-style: normal;
}

.trip-row__footer {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.trip-source {
  padding: 1px 6px;
  border-radius: 4px;
  color: #6983a5;
  background: #f2f7fd;
}

.trip-row__arrow {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: #a5b8ce;
  font-size: 13px;
  place-items: center;
  background: #f6faff;
  transition:
    color 0.18s ease,
    background 0.18s ease;
}

.trip-row:hover .trip-row__arrow {
  color: var(--ta-primary);
  background: #eaf4ff;
}

.trip-pagination {
  display: flex;
  justify-content: center;
  padding: 14px 20px 18px;
  border-top: 1px solid #f0f6fc;
}

@media (max-width: 760px) {
  .trips-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .trips-toolbar__search {
    flex: 0 0 auto;
    width: 100%;
    max-width: none;
  }

  .trips-toolbar__meta {
    justify-content: space-between;
  }

  .trips-toolbar__sort {
    display: none;
  }

  .trip-list {
    padding: 4px 8px;
  }

  .trip-row {
    gap: 11px;
    padding: 10px;
  }

  .trip-row__title-line strong {
    font-size: 14.5px;
  }

  .trip-row__dates {
    font-size: 10.5px;
  }

  .trip-row__footer {
    gap: 6px;
    font-size: 9px;
  }

  .trip-row__arrow {
    display: none;
  }

  .trip-skeleton__cover {
    width: 104px;
    height: 82px;
  }
}
</style>
