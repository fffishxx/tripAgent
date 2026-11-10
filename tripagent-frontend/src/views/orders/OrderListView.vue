<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  Plus,
  Refresh,
  Tickets,
  Van,
  WarningFilled,
} from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { getTaxiOrders } from '@/api/taxi'
import type { TaxiOrder } from '@/types/taxi'
import { formatDateTime, formatFare } from '@/utils/format'
import {
  TAXI_STATUS_FILTERS,
  formatTaxiDistance,
  formatTaxiDuration,
  getTaxiProviderLabel,
  getTaxiStatusLabel,
  getTaxiStatusTone,
} from '@/utils/taxi'

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 6

const initialPage = Number.parseInt(
  typeof route.query.page === 'string' ? route.query.page : '1',
  10,
)
const initialStatus =
  typeof route.query.status === 'string' ? route.query.status : ''

const orders = ref<TaxiOrder[]>([])
const total = ref(0)
const pageNum = ref(
  Number.isFinite(initialPage) && initialPage > 0 ? initialPage : 1,
)
const statusFilter = ref(
  TAXI_STATUS_FILTERS.some((item) => item.key === initialStatus)
    ? initialStatus
    : '',
)
const loading = ref(true)
const loadError = ref('')
let requestSeq = 0

function syncQuery(): void {
  const query: Record<string, string> = {}
  if (statusFilter.value) {
    query.status = statusFilter.value
  }
  if (pageNum.value > 1) {
    query.page = String(pageNum.value)
  }
  void router.replace({ name: 'orders', query })
}

const activeFilterLabel = computed(
  () =>
    TAXI_STATUS_FILTERS.find((item) => item.key === statusFilter.value)?.label ??
    '全部',
)

async function fetchOrders(resetPage = false): Promise<void> {
  if (resetPage) {
    pageNum.value = 1
  }

  const seq = ++requestSeq
  loading.value = true
  loadError.value = ''

  try {
    const page = await getTaxiOrders({
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
      status: statusFilter.value || undefined,
    })
    if (seq !== requestSeq) {
      return
    }

    orders.value = page.records ?? []
    total.value = page.total ?? 0

    const lastPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
    if (pageNum.value > lastPage) {
      pageNum.value = lastPage
      syncQuery()
      void fetchOrders()
    }
  } catch (error) {
    if (seq !== requestSeq) {
      return
    }
    orders.value = []
    total.value = 0
    loadError.value =
      error instanceof Error ? error.message : '订单列表加载失败'
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

onMounted(() => {
  void fetchOrders()
})

function handleFilter(key: string): void {
  if (statusFilter.value === key) {
    return
  }
  statusFilter.value = key
  void fetchOrders(true)
  syncQuery()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  syncQuery()
  void fetchOrders()
}

function openOrder(order: TaxiOrder): void {
  void router.push({
    name: 'order-detail',
    params: {
      orderId: order.id,
    },
  })
}

function goCreate(): void {
  void router.push({ name: 'order-create' })
}

function clearFilter(): void {
  statusFilter.value = ''
  void fetchOrders(true)
  syncQuery()
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="我的订单"
      description="查看和管理你的模拟打车订单。"
      :icon="Tickets"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="goCreate">
          模拟打车
        </el-button>
      </template>
    </PageHeader>

    <div class="mock-notice">
      <span class="mock-notice__icon">
        <el-icon><Van /></el-icon>
      </span>
      <div>
        <strong>这里只有模拟订单</strong>
        <p>
          用于功能演示，不接入真实支付，也不会向真实打车平台派单；订单独立于行程。
        </p>
      </div>
      <span class="stage-badge">模拟</span>
    </div>

    <section class="orders-card surface-card">
      <div class="orders-toolbar">
        <div class="filter-chips" aria-label="订单状态筛选">
          <button
            v-for="filter in TAXI_STATUS_FILTERS"
            :key="filter.key"
            type="button"
            class="filter-chip"
            :class="{
              'filter-chip--active': statusFilter === filter.key,
            }"
            @click="handleFilter(filter.key)"
          >
            {{ filter.label }}
          </button>
        </div>

        <div class="orders-toolbar__meta">
          <span v-if="!loading && !loadError">
            {{ activeFilterLabel }} {{ total }} 单
          </span>
          <el-tooltip content="刷新订单列表" placement="top">
            <el-button
              circle
              :icon="Refresh"
              :loading="loading"
              aria-label="刷新订单列表"
              @click="fetchOrders()"
            />
          </el-tooltip>
        </div>
      </div>

      <div v-if="loading" class="orders-loading">
        <div v-for="index in 4" :key="index" class="order-skeleton">
          <el-skeleton :rows="2" animated />
        </div>
      </div>

      <div v-else-if="loadError" class="orders-state">
        <span class="orders-state__icon orders-state__icon--warning">
          <el-icon><WarningFilled /></el-icon>
        </span>
        <strong>订单列表加载失败</strong>
        <p>{{ loadError }}</p>
        <el-button type="primary" round @click="fetchOrders()">
          重新加载
        </el-button>
      </div>

      <div v-else-if="orders.length === 0" class="orders-empty">
        <EmptyState
          :icon="Van"
          title="暂无打车订单"
          description="从模拟打车开始：选择起终点、查看模拟估价、选择车型并创建一条演示订单。"
        >
          <el-button type="primary" round :icon="Plus" @click="goCreate">
            模拟打车
          </el-button>
          <el-button
            v-if="statusFilter"
            round
            @click="clearFilter"
          >
            查看全部订单
          </el-button>
        </EmptyState>
      </div>

      <template v-else>
        <div class="orders-list">
          <button
            v-for="order in orders"
            :key="order.id"
            type="button"
            class="order-row"
            :aria-label="`查看订单 ${order.orderNo}`"
            @click="openOrder(order)"
          >
            <span class="order-row__icon">
              <el-icon><Van /></el-icon>
            </span>

            <span class="order-row__main">
              <span class="order-row__top">
                <span class="order-row__no">{{ order.orderNo }}</span>
                <span
                  class="order-status"
                  :class="`order-status--${getTaxiStatusTone(order.status)}`"
                >
                  {{ getTaxiStatusLabel(order.status, order.statusText) }}
                </span>
                <span class="order-mock">模拟</span>
              </span>

              <span class="order-row__route">
                <span>{{ order.pickup.name }}</span>
                <el-icon><ArrowRight /></el-icon>
                <span>{{ order.destination.name }}</span>
              </span>

              <span class="order-row__meta">
                <span v-if="order.city">{{ order.city }}</span>
                <span>{{ order.vehicleType || '车型待定' }}</span>
                <span v-if="order.distanceM != null">
                  {{ formatTaxiDistance(order.distanceM) }}
                </span>
                <span v-if="order.durationMin != null">
                  {{ formatTaxiDuration(order.durationMin) }}
                </span>
                <span>{{ getTaxiProviderLabel(order.provider) }}</span>
                <span>创建于 {{ formatDateTime(order.createTime) }}</span>
              </span>
            </span>

            <span class="order-row__fare">
              <strong>{{ formatFare(order.estimatedFare) }}</strong>
              <small>模拟车费</small>
            </span>

            <span class="order-row__arrow">
              <el-icon><ArrowRight /></el-icon>
            </span>
          </button>
        </div>

        <div v-if="total > PAGE_SIZE" class="orders-pagination">
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
.mock-notice {
  display: flex;
  align-items: flex-start;
  gap: 13px;
  padding: 15px 19px;
  border: 1px solid rgba(214, 231, 249, 0.92);
  border-radius: var(--ta-radius-md);
  background: linear-gradient(135deg, rgba(239, 247, 255, 0.94), rgba(248, 252, 255, 0.9));
}

.mock-notice__icon {
  display: grid;
  flex: 0 0 auto;
  width: 37px;
  height: 37px;
  border-radius: 12px;
  color: var(--ta-primary);
  font-size: 18px;
  place-items: center;
  background: #e6f1ff;
}

.mock-notice div {
  flex: 1;
  min-width: 0;
}

.mock-notice strong {
  color: var(--ta-ink);
  font-size: 12.5px;
  font-weight: 600;
}

.mock-notice p {
  margin-top: 2px;
  color: var(--ta-ink-secondary);
  font-size: 11px;
  line-height: 1.65;
}

.orders-card {
  overflow: hidden;
}

.orders-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 17px 22px;
  border-bottom: 1px solid #eef5fd;
}

.orders-toolbar__meta {
  display: flex;
  align-items: center;
  gap: 11px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.orders-loading {
  padding: 8px 22px;
}

.order-skeleton {
  padding: 13px 0;
}

.order-skeleton + .order-skeleton {
  border-top: 1px dashed #eef4fb;
}

.orders-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  padding: 54px 24px;
  text-align: center;
}

.orders-state__icon {
  display: grid;
  width: 46px;
  height: 46px;
  margin-bottom: 5px;
  border-radius: 14px;
  color: #c9821d;
  font-size: 22px;
  place-items: center;
  background: #fff6e8;
}

.orders-state strong {
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
}

.orders-state p {
  max-width: 420px;
  color: var(--ta-ink-muted);
  font-size: 11.5px;
  line-height: 1.7;
}

.orders-state .el-button {
  margin-top: 7px;
}

.orders-empty {
  overflow: hidden;
}

.orders-list {
  padding: 6px 14px;
}

.order-row {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  padding: 13px;
  border: 1px solid transparent;
  border-radius: 15px;
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.order-row + .order-row {
  margin-top: 2px;
}

.order-row:hover {
  border-color: #e4effb;
  background: #f9fcff;
  box-shadow: 0 10px 24px rgba(47, 100, 170, 0.07);
  transform: translateY(-1px);
}

.order-row__icon {
  display: grid;
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  color: var(--ta-primary);
  font-size: 21px;
  place-items: center;
  background: #eaf4ff;
}

.order-row__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.order-row__top {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.order-row__no {
  color: var(--ta-ink);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 11px;
  font-weight: 600;
}

.order-status {
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 500;
}

.order-status--ongoing {
  color: #2a6cc7;
  background: #eaf4ff;
}

.order-status--success {
  color: #1e835c;
  background: #e9f8f1;
}

.order-status--muted {
  color: #7c8fa6;
  background: #f0f4f8;
}

.order-mock {
  padding: 1px 5px;
  border: 1px solid #f0dfbe;
  border-radius: 4px;
  color: #a57417;
  font-size: 8.5px;
  background: #fff8ec;
}

.order-row__route {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--ta-ink-secondary);
  font-size: 12px;
  min-width: 0;
}

.order-row__route > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-row__route .el-icon {
  flex: 0 0 auto;
  color: #a6b8ce;
  font-size: 11px;
}

.order-row__meta {
  display: flex;
  gap: 9px;
  flex-wrap: wrap;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
}

.order-row__meta span {
  position: relative;
}

.order-row__meta span + span::before {
  margin-right: 9px;
  color: #d2deeb;
  content: '·';
}

.order-row__fare {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-end;
  flex-direction: column;
  gap: 1px;
}

.order-row__fare strong {
  color: var(--ta-ink);
  font-size: 14px;
  font-weight: 600;
}

.order-row__fare small {
  color: var(--ta-ink-muted);
  font-size: 9px;
}

.order-row__arrow {
  display: grid;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: #a5b8ce;
  font-size: 12px;
  place-items: center;
  background: #f6faff;
}

.order-row:hover .order-row__arrow {
  color: var(--ta-primary);
  background: #eaf4ff;
}

.orders-pagination {
  display: flex;
  justify-content: center;
  padding: 13px 20px 17px;
  border-top: 1px solid #f0f6fc;
}

@media (max-width: 760px) {
  .orders-toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
    padding: 14px;
  }

  .orders-toolbar__meta {
    justify-content: flex-end;
  }

  .mock-notice {
    flex-wrap: wrap;
  }

  .mock-notice .stage-badge {
    margin-left: 50px;
  }

  .orders-list {
    padding: 4px 8px;
  }

  .order-row {
    align-items: flex-start;
    gap: 9px;
    padding: 11px 9px;
  }

  .order-row__icon {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    font-size: 18px;
  }

  .order-row__meta {
    gap: 5px;
  }

  .order-row__meta span + span::before {
    margin-right: 5px;
  }

  .order-row__fare {
    padding-top: 2px;
  }

  .order-row__arrow {
    display: none;
  }
}
</style>
