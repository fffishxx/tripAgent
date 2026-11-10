<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  CircleCheck,
  Clock,
  Flag,
  Location,
  Money,
  Position,
  Tickets,
  Van,
  WarningFilled,
} from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  cancelTaxiOrder,
  completeTaxiOrder,
  getTaxiOrder,
} from '@/api/taxi'
import type { TaxiOrder } from '@/types/taxi'
import { formatDateTime, formatFare } from '@/utils/format'
import {
  formatTaxiDistance,
  formatTaxiDuration,
  getTaxiProviderLabel,
  getTaxiStatusLabel,
  getTaxiStatusTone,
} from '@/utils/taxi'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => String(route.params.orderId ?? ''))
const order = ref<TaxiOrder | null>(null)
const loading = ref(true)
const loadError = ref('')
const cancelling = ref(false)
const completing = ref(false)
const cancelDialogVisible = ref(false)
const cancelReason = ref('')

const canAct = computed(() => order.value?.status === 'CREATED')

const statusLabel = computed(() =>
  order.value
    ? getTaxiStatusLabel(order.value.status, order.value.statusText)
    : '',
)

const statusTone = computed(() =>
  order.value ? getTaxiStatusTone(order.value.status) : 'muted',
)

async function loadOrder(): Promise<void> {
  if (!orderId.value) {
    loadError.value = '缺少订单 ID'
    loading.value = false
    return
  }

  loading.value = true
  loadError.value = ''
  try {
    order.value = await getTaxiOrder(orderId.value)
  } catch (error) {
    order.value = null
    loadError.value =
      error instanceof Error ? error.message : '订单详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadOrder()
})

function goBack(): void {
  void router.push({ name: 'orders' })
}

function goCreate(): void {
  void router.push({ name: 'order-create' })
}

function openCancelDialog(): void {
  cancelReason.value = ''
  cancelDialogVisible.value = true
}

async function confirmCancel(): Promise<void> {
  if (!order.value || cancelling.value) {
    return
  }

  cancelling.value = true
  try {
    const updated = await cancelTaxiOrder(
      order.value.id,
      cancelReason.value.trim() || undefined,
    )
    order.value = updated
    cancelDialogVisible.value = false
    ElMessage.success('模拟订单已取消')
  } catch {
    cancelDialogVisible.value = false
    await loadOrder()
  } finally {
    cancelling.value = false
  }
}

async function handleComplete(): Promise<void> {
  if (!order.value || completing.value || !canAct.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      '这会把订单标记为“已完成”，仅用于演示，不会发生真实支付或真实行程结束。',
      '模拟完成订单',
      {
        confirmButtonText: '模拟完成',
        cancelButtonText: '取消',
        type: 'info',
      },
    )
  } catch {
    return
  }

  completing.value = true
  try {
    const updated = await completeTaxiOrder(order.value.id)
    order.value = updated
    ElMessage.success('模拟订单已完成')
  } catch {
    await loadOrder()
  } finally {
    completing.value = false
  }
}
</script>

<template>
  <div class="page order-detail-page">
    <PageHeader
      title="订单详情"
      description="查看模拟打车订单的状态、路线和费用。"
      :icon="Tickets"
    >
      <template #actions>
        <el-button text :icon="ArrowLeft" @click="goBack">
          返回我的订单
        </el-button>
      </template>
    </PageHeader>

    <div v-if="loading" class="order-detail-loading surface-card">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="loadError || !order" class="order-detail-error surface-card">
      <EmptyState
        :icon="WarningFilled"
        title="订单加载失败"
        :description="loadError || '该订单不存在或已被删除。'"
      >
        <el-button round @click="goBack">返回我的订单</el-button>
        <el-button type="primary" round @click="loadOrder">重新加载</el-button>
      </EmptyState>
    </div>

    <template v-else>
      <section class="order-hero surface-card">
        <span class="order-hero__icon">
          <el-icon><Van /></el-icon>
        </span>

        <div class="order-hero__main">
          <div class="order-hero__title">
            <span class="order-hero__no">{{ order.orderNo }}</span>
            <span
              class="order-status"
              :class="`order-status--${statusTone}`"
            >
              {{ statusLabel }}
            </span>
            <span class="order-hero__mock">模拟订单</span>
          </div>
          <p>
            {{ order.pickup.name }}
            <span>→</span>
            {{ order.destination.name }}
            <template v-if="order.city">· {{ order.city }}</template>
          </p>
          <small>
            创建于 {{ formatDateTime(order.createTime) }}
            <template v-if="order.updateTime !== order.createTime">
              · 更新于 {{ formatDateTime(order.updateTime) }}
            </template>
          </small>
        </div>

        <div class="order-hero__actions">
          <template v-if="canAct">
            <el-button
              type="danger"
              plain
              :disabled="completing"
              @click="openCancelDialog"
            >
              取消订单
            </el-button>
            <el-button
              type="primary"
              :loading="completing"
              @click="handleComplete"
            >
              模拟完成
            </el-button>
          </template>
          <el-button v-else round @click="goCreate">
            再叫一辆
          </el-button>
        </div>
      </section>

      <div class="mock-notice">
        <span class="mock-notice__icon">
          <el-icon><Van /></el-icon>
        </span>
        <div>
          <strong>这是模拟打车订单</strong>
          <p>
            {{ getTaxiProviderLabel(order.provider) }}，未接入真实打车平台、真实派单或支付。
            订单独立于行程。
          </p>
        </div>
      </div>

      <div class="order-detail-layout">
        <section class="order-route-card surface-card">
          <header class="detail-card-head">
            <div>
              <h2>行程路线</h2>
              <p>模拟路线与费用信息</p>
            </div>
            <span class="detail-card-head__fare">
              {{ formatFare(order.estimatedFare) }}
            </span>
          </header>

          <div class="taxi-route">
            <div class="taxi-route__stop">
              <span class="taxi-route__dot taxi-route__dot--pickup">
                <el-icon><Position /></el-icon>
              </span>
              <div>
                <small>上车点</small>
                <strong>{{ order.pickup.name }}</strong>
                <p v-if="order.pickup.address">
                  {{ order.pickup.address }}
                </p>
              </div>
            </div>

            <span class="taxi-route__line" aria-hidden="true" />

            <div class="taxi-route__stop">
              <span class="taxi-route__dot taxi-route__dot--destination">
                <el-icon><Flag /></el-icon>
              </span>
              <div>
                <small>目的地</small>
                <strong>{{ order.destination.name }}</strong>
                <p v-if="order.destination.address">
                  {{ order.destination.address }}
                </p>
              </div>
            </div>
          </div>

          <div class="taxi-route__meta">
            <div>
              <el-icon><Location /></el-icon>
              <span>{{ formatTaxiDistance(order.distanceM) }}</span>
            </div>
            <div>
              <el-icon><Clock /></el-icon>
              <span>{{ formatTaxiDuration(order.durationMin) }}</span>
            </div>
            <div>
              <el-icon><Van /></el-icon>
              <span>{{ order.vehicleType || '车型待定' }}</span>
            </div>
            <div>
              <el-icon><Money /></el-icon>
              <span>{{ order.currency || 'CNY' }}</span>
            </div>
          </div>

          <div class="taxi-note">
            <el-icon><WarningFilled /></el-icon>
            <span>车费为 Java 模拟计价结果，不产生真实扣费。</span>
          </div>
        </section>

        <aside class="order-aside">
          <section class="order-status-card surface-card">
            <header class="detail-card-head">
              <div>
                <h2>订单状态</h2>
                <p>{{ statusLabel }}</p>
              </div>
              <span
                class="status-dot"
                :class="`status-dot--${statusTone}`"
              />
            </header>

            <ol class="status-timeline">
              <li class="status-timeline__item status-timeline__item--done">
                <span class="status-timeline__dot">
                  <el-icon><CircleCheck /></el-icon>
                </span>
                <div>
                  <strong>订单已创建</strong>
                  <small>{{ formatDateTime(order.createTime) }}</small>
                </div>
              </li>

              <li
                v-if="order.status === 'COMPLETED'"
                class="status-timeline__item status-timeline__item--done"
              >
                <span class="status-timeline__dot">
                  <el-icon><CircleCheck /></el-icon>
                </span>
                <div>
                  <strong>模拟完成</strong>
                  <small>
                    {{ order.completedTime ? formatDateTime(order.completedTime) : '已完成' }}
                  </small>
                </div>
              </li>

              <li
                v-else-if="order.status === 'CANCELLED'"
                class="status-timeline__item status-timeline__item--cancelled"
              >
                <span class="status-timeline__dot">
                  <el-icon><WarningFilled /></el-icon>
                </span>
                <div>
                  <strong>订单已取消</strong>
                  <small>
                    {{ order.cancelledTime ? formatDateTime(order.cancelledTime) : '已取消' }}
                  </small>
                  <p v-if="order.cancelReason">
                    原因：{{ order.cancelReason }}
                  </p>
                </div>
              </li>

              <li
                v-else
                class="status-timeline__item status-timeline__item--pending"
              >
                <span class="status-timeline__dot" />
                <div>
                  <strong>等待模拟完成</strong>
                  <small>可以取消订单或点击模拟完成。</small>
                </div>
              </li>
            </ol>
          </section>

          <section class="order-info-card surface-card">
            <header class="detail-card-head">
              <div>
                <h2>订单信息</h2>
                <p>模拟订单记录</p>
              </div>
            </header>

            <div class="order-info-row">
              <span>订单号</span>
              <strong class="order-info-row__no">{{ order.orderNo }}</strong>
            </div>
            <div class="order-info-row">
              <span>来源</span>
              <strong>{{ getTaxiProviderLabel(order.provider) }}</strong>
            </div>
            <div class="order-info-row">
              <span>车型</span>
              <strong>{{ order.vehicleType || '--' }}</strong>
            </div>
            <div class="order-info-row">
              <span>模拟车费</span>
              <strong>{{ formatFare(order.estimatedFare) }}</strong>
            </div>
            <div class="order-info-row">
              <span>创建时间</span>
              <strong>{{ formatDateTime(order.createTime) }}</strong>
            </div>
            <div class="order-info-row">
              <span>最后更新</span>
              <strong>{{ formatDateTime(order.updateTime) }}</strong>
            </div>
            <div
              v-if="order.cancelledTime"
              class="order-info-row"
            >
              <span>取消时间</span>
              <strong>{{ formatDateTime(order.cancelledTime) }}</strong>
            </div>
            <div
              v-if="order.completedTime"
              class="order-info-row"
            >
              <span>完成时间</span>
              <strong>{{ formatDateTime(order.completedTime) }}</strong>
            </div>
          </section>
        </aside>
      </div>
    </template>

    <el-dialog
      v-model="cancelDialogVisible"
      title="取消模拟订单"
      width="min(470px, 92vw)"
      :close-on-click-modal="false"
      append-to-body
    >
      <div class="cancel-dialog">
        <p>
          取消后订单状态会变为“已取消”。这是模拟订单，不影响任何真实出行。
        </p>
        <el-input
          v-model="cancelReason"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          resize="none"
          placeholder="取消原因（可选）"
        />
      </div>
      <template #footer>
        <el-button :disabled="cancelling" @click="cancelDialogVisible = false">
          保留订单
        </el-button>
        <el-button
          type="danger"
          :loading="cancelling"
          @click="confirmCancel"
        >
          确认取消
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-detail-page {
  max-width: 1120px;
  margin: 0 auto;
}

.order-detail-loading {
  padding: 30px;
}

.order-detail-error {
  overflow: hidden;
}

.order-hero {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 22px;
  background:
    radial-gradient(circle at 92% 10%, rgba(117, 174, 255, 0.15), transparent 32%),
    rgba(255, 255, 255, 0.94);
}

.order-hero__icon {
  display: grid;
  flex: 0 0 auto;
  width: 56px;
  height: 56px;
  border-radius: 17px;
  color: var(--ta-primary);
  font-size: 27px;
  place-items: center;
  background: linear-gradient(145deg, #eff7ff, #dcecff);
  box-shadow: 0 10px 24px rgba(47, 126, 247, 0.12);
}

.order-hero__main {
  flex: 1;
  min-width: 0;
}

.order-hero__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.order-hero__no {
  color: var(--ta-ink-strong);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 16px;
  font-weight: 600;
}

.order-status {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 9.5px;
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

.order-hero__mock {
  padding: 1px 6px;
  border: 1px solid #f0dfbe;
  border-radius: 4px;
  color: #a57417;
  font-size: 8.5px;
  background: #fff8ec;
}

.order-hero__main p {
  margin-top: 5px;
  color: var(--ta-ink-secondary);
  font-size: 12.5px;
}

.order-hero__main p span {
  margin: 0 5px;
  color: #a4b7cc;
}

.order-hero__main small {
  display: block;
  margin-top: 3px;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.order-hero__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.mock-notice {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid rgba(214, 231, 249, 0.92);
  border-radius: var(--ta-radius-md);
  background: linear-gradient(135deg, rgba(239, 247, 255, 0.94), rgba(248, 252, 255, 0.9));
}

.mock-notice__icon {
  display: grid;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--ta-primary);
  font-size: 17px;
  place-items: center;
  background: #e6f1ff;
}

.mock-notice div {
  flex: 1;
  min-width: 0;
}

.mock-notice strong {
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
}

.mock-notice p {
  margin-top: 2px;
  color: var(--ta-ink-secondary);
  font-size: 10.5px;
  line-height: 1.65;
}

.order-detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr);
  align-items: start;
  gap: 18px;
}

.order-route-card,
.order-status-card,
.order-info-card {
  padding: 20px;
}

.detail-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-card-head h2 {
  color: var(--ta-ink);
  font-size: 15px;
  font-weight: 600;
}

.detail-card-head p {
  margin-top: 2px;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.detail-card-head__fare {
  color: var(--ta-ink);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.taxi-route {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 0;
  padding: 16px;
  border: 1px solid #e7f0fa;
  border-radius: 13px;
  background: #fbfdff;
}

.taxi-route__stop {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
}

.taxi-route__stop > div {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.taxi-route__stop small {
  color: var(--ta-ink-muted);
  font-size: 9px;
}

.taxi-route__stop strong {
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
}

.taxi-route__stop p {
  margin-top: 1px;
  color: var(--ta-ink-muted);
  font-size: 10px;
  line-height: 1.5;
}

.taxi-route__dot {
  display: grid;
  flex: 0 0 auto;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  font-size: 13px;
  place-items: center;
}

.taxi-route__dot--pickup {
  color: #2f7ef7;
  background: #eaf4ff;
}

.taxi-route__dot--destination {
  color: #22a978;
  background: #e9f8f1;
}

.taxi-route__line {
  width: 1px;
  height: 22px;
  margin: 4px 0 4px 13px;
  background: repeating-linear-gradient(
    180deg,
    #c7dcf5 0 4px,
    transparent 4px 8px
  );
}

.taxi-route__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.taxi-route__meta div {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  border: 1px solid #eaf2fb;
  border-radius: 7px;
  color: var(--ta-ink-secondary);
  font-size: 10px;
  background: #fbfdff;
}

.taxi-route__meta .el-icon {
  color: var(--ta-primary);
}

.taxi-note {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 13px;
  padding: 9px 10px;
  border: 1px dashed #eddfc5;
  border-radius: 8px;
  color: #8a6b32;
  font-size: 9.5px;
  line-height: 1.6;
  background: #fffbf3;
}

.order-aside {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-dot {
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 50%;
}

.status-dot--ongoing {
  background: var(--ta-primary);
  box-shadow: 0 0 0 4px rgba(47, 126, 247, 0.13);
}

.status-dot--success {
  background: #22a978;
  box-shadow: 0 0 0 4px rgba(34, 169, 120, 0.13);
}

.status-dot--muted {
  background: #99a9bb;
  box-shadow: 0 0 0 4px rgba(153, 169, 187, 0.12);
}

.status-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.status-timeline__item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  min-height: 48px;
  position: relative;
}

.status-timeline__item:not(:last-child)::after {
  position: absolute;
  top: 22px;
  bottom: 4px;
  left: 10px;
  width: 1px;
  content: '';
  background: #e4eef9;
}

.status-timeline__dot {
  display: grid;
  flex: 0 0 auto;
  width: 21px;
  height: 21px;
  border: 1px solid #dce9f8;
  border-radius: 50%;
  color: #8ba4c0;
  font-size: 11px;
  place-items: center;
  background: #f7fbff;
}

.status-timeline__item--done .status-timeline__dot {
  color: #1f8b61;
  border-color: #d3ece0;
  background: #f0fbf6;
}

.status-timeline__item--cancelled .status-timeline__dot {
  color: #a87419;
  border-color: #f0e0c4;
  background: #fffaf1;
}

.status-timeline__dot--pending,
.status-timeline__item--pending .status-timeline__dot {
  background: #fff;
}

.status-timeline__item > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
  padding-top: 1px;
}

.status-timeline__item strong {
  color: var(--ta-ink);
  font-size: 11px;
  font-weight: 600;
}

.status-timeline__item small,
.status-timeline__item p {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.6;
}

.status-timeline__item p {
  margin-top: 2px;
  color: #8a6b32;
}

.order-info-card .detail-card-head {
  margin-bottom: 8px;
}

.order-info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.order-info-row + .order-info-row {
  border-top: 1px dashed #f0f5fb;
}

.order-info-row strong {
  overflow: hidden;
  color: var(--ta-ink-secondary);
  font-size: 10px;
  font-weight: 500;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-info-row__no {
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.cancel-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cancel-dialog p {
  color: var(--ta-ink-secondary);
  font-size: 11.5px;
  line-height: 1.7;
}

@media (max-width: 960px) {
  .order-detail-layout {
    grid-template-columns: 1fr;
  }

  .order-aside {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
}

@media (max-width: 760px) {
  .order-hero {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 16px;
  }

  .order-hero__icon {
    width: 44px;
    height: 44px;
    border-radius: 13px;
    font-size: 21px;
  }

  .order-hero__main {
    flex-basis: calc(100% - 60px);
  }

  .order-hero__no {
    font-size: 14px;
  }

  .order-hero__actions {
    width: 100%;
    justify-content: flex-end;
  }

  .order-aside {
    display: flex;
  }

  .order-route-card,
  .order-status-card,
  .order-info-card {
    padding: 16px;
  }
}
</style>
