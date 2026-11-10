<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Clock,
  Flag,
  Location,
  Money,
  Position,
  Sort,
  Van,
  WarningFilled,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import TaxiLocationPicker from '@/components/taxi/TaxiLocationPicker.vue'
import { createTaxiOrder, estimateTaxi } from '@/api/taxi'
import { hasAmapKey } from '@/config/amap'
import type {
  TaxiEstimate,
  TaxiLocation,
} from '@/types/taxi'
import { formatFare } from '@/utils/format'
import {
  formatTaxiDistance,
  formatTaxiDuration,
  sameTaxiPoint,
  toTaxiPoint,
} from '@/utils/taxi'

const router = useRouter()

const pickup = ref<TaxiLocation | null>(null)
const destination = ref<TaxiLocation | null>(null)
const city = ref('')
const estimate = ref<TaxiEstimate | null>(null)
const selectedVehicleType = ref('')
const estimating = ref(false)
const creating = ref(false)
const estimateError = ref('')
let leavingAfterCreate = false
let disposed = false

const hasDraft = computed(
  () =>
    Boolean(pickup.value || destination.value || estimate.value) ||
    Boolean(city.value.trim()),
)

const canEstimate = computed(
  () => Boolean(pickup.value && destination.value) && !estimating.value,
)

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (hasDraft.value && !leavingAfterCreate) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  disposed = true
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(async () => {
  if (leavingAfterCreate) {
    return true
  }
  if (creating.value) {
    ElMessage.warning('正在创建模拟订单，请稍候')
    return false
  }
  if (!hasDraft.value) {
    return true
  }

  try {
    await ElMessageBox.confirm(
      '当前已选择地点或完成估价，离开后这些内容会丢失。',
      '离开模拟打车？',
      {
        confirmButtonText: '离开',
        cancelButtonText: '继续填写',
        type: 'warning',
      },
    )
    return true
  } catch {
    return false
  }
})

const selectedVehicle = computed(
  () =>
    estimate.value?.vehicleOptions.find(
      (option) => option.vehicleType === selectedVehicleType.value,
    ) ?? null,
)

function resetEstimate(): void {
  estimate.value = null
  selectedVehicleType.value = ''
  estimateError.value = ''
}

watch(
  [pickup, destination, city],
  () => {
    resetEstimate()
  },
  { deep: false },
)

watch(pickup, (value) => {
  if (value?.city && !city.value.trim()) {
    city.value = value.city
  }
})

function swapLocations(): void {
  const previousPickup = pickup.value
  pickup.value = destination.value
  destination.value = previousPickup
}

async function handleEstimate(): Promise<void> {
  if (!pickup.value || !destination.value) {
    ElMessage.warning('请先选择上车点和目的地')
    return
  }
  if (sameTaxiPoint(pickup.value, destination.value)) {
    ElMessage.warning('上车点和目的地不能相同')
    return
  }

  estimating.value = true
  estimateError.value = ''
  try {
    const result = await estimateTaxi({
      pickup: toTaxiPoint(pickup.value),
      destination: toTaxiPoint(destination.value),
      city: city.value.trim() || undefined,
    })
    estimate.value = result
    selectedVehicleType.value = result.vehicleOptions[0]?.vehicleType ?? ''
  } catch (error) {
    estimate.value = null
    selectedVehicleType.value = ''
    estimateError.value =
      error instanceof Error ? error.message : '模拟估价失败，请稍后重试'
  } finally {
    estimating.value = false
  }
}

async function handleCreate(): Promise<void> {
  if (!pickup.value || !destination.value || !estimate.value) {
    return
  }
  if (!selectedVehicle.value) {
    ElMessage.warning('请选择车型')
    return
  }
  if (creating.value) {
    return
  }

  creating.value = true
  try {
    const order = await createTaxiOrder({
      pickup: toTaxiPoint(pickup.value),
      destination: toTaxiPoint(destination.value),
      city: city.value.trim() || undefined,
      vehicleType: selectedVehicle.value.vehicleType,
    })
    if (disposed) {
      return
    }
    leavingAfterCreate = true
    ElMessage.success('模拟订单已创建')
    await router.replace({
      name: 'order-detail',
      params: {
        orderId: order.id,
      },
    })
  } catch {
    // 创建失败信息由请求层统一提示。
  } finally {
    creating.value = false
  }
}

function goBack(): void {
  void router.push({ name: 'orders' })
}
</script>

<template>
  <div class="page order-create-page">
    <PageHeader
      title="模拟打车"
      description="选择起终点，查看模拟估价，创建一条仅用于演示的打车订单。"
      :icon="Van"
    >
      <template #actions>
        <el-button text :icon="ArrowLeft" @click="goBack">
          返回我的订单
        </el-button>
      </template>
    </PageHeader>

    <div class="mock-notice">
      <span class="mock-notice__icon">
        <el-icon><Van /></el-icon>
      </span>
      <div>
        <strong>本流程完全模拟</strong>
        <p>
          不接入真实支付，也不会向真实打车平台派单；订单独立于行程，仅用于功能演示。
        </p>
      </div>
      <span class="mock-notice__chip">仅演示</span>
    </div>

    <el-alert
      v-if="!hasAmapKey"
      type="warning"
      :closable="false"
      show-icon
      title="未配置高德地图 Key"
      description="地点搜索依赖 VITE_AMAP_KEY，请在 .env 中配置后重启开发服务。"
      class="create-alert"
    />

    <div class="create-layout">
      <section class="route-form-card surface-card">
        <header class="card-head">
          <div>
            <h2>行程路线</h2>
            <p>从高德地点建议中选择上车点和目的地。</p>
          </div>
          <span class="card-head__icon">
            <el-icon><Position /></el-icon>
          </span>
        </header>

        <el-form label-position="top" class="route-form">
          <el-form-item label="城市（可选）">
            <el-input
              v-model="city"
              maxlength="64"
              :prefix-icon="Location"
              placeholder="例如：杭州；用于缩小地点搜索范围"
            />
          </el-form-item>

          <div class="route-field">
            <div class="route-field__marker route-field__marker--pickup">
              <el-icon><Position /></el-icon>
            </div>
            <div class="route-field__body">
              <span class="route-field__label">上车点</span>
              <TaxiLocationPicker
                v-model="pickup"
                :city="city"
                placeholder="搜索上车地点，例如：杭州东站"
              />
            </div>
          </div>

          <div class="route-swap">
            <span class="route-swap__line" />
            <el-tooltip content="交换起终点" placement="right">
              <el-button
                circle
                size="small"
                :icon="Sort"
                :disabled="!pickup && !destination"
                aria-label="交换起终点"
                @click="swapLocations"
              />
            </el-tooltip>
          </div>

          <div class="route-field">
            <div class="route-field__marker route-field__marker--destination">
              <el-icon><Flag /></el-icon>
            </div>
            <div class="route-field__body">
              <span class="route-field__label">目的地</span>
              <TaxiLocationPicker
                v-model="destination"
                :city="city"
                placeholder="搜索目的地，例如：西湖"
              />
            </div>
          </div>

          <el-button
            type="primary"
            size="large"
            :icon="Money"
            class="estimate-button"
            :loading="estimating"
            :disabled="!canEstimate"
            @click="handleEstimate"
          >
            开始模拟估价
          </el-button>
        </el-form>

        <p class="route-form__note">
          估价由 Java 后端按模拟规则计算，服务端创建订单时会重新计算，不信任前端金额。
        </p>
      </section>

      <section class="estimate-card surface-card">
        <header class="card-head">
          <div>
            <h2>模拟估价</h2>
            <p>选择车型后创建模拟订单。</p>
          </div>
          <span class="estimate-mock-tag">MOCK</span>
        </header>

        <div v-if="estimating" class="estimate-state">
          <span class="estimate-spinner" aria-hidden="true" />
          <strong>正在获取模拟估价</strong>
          <p>根据高德地点坐标计算模拟距离与费用…</p>
        </div>

        <div v-else-if="estimateError" class="estimate-state estimate-state--error">
          <span class="estimate-state__icon">
            <el-icon><WarningFilled /></el-icon>
          </span>
          <strong>估价失败</strong>
          <p>{{ estimateError }}</p>
          <el-button size="small" round @click="handleEstimate">
            重新估价
          </el-button>
        </div>

        <div v-else-if="estimate" class="estimate-result">
          <div class="estimate-summary">
            <div>
              <el-icon><Location /></el-icon>
              <span>{{ formatTaxiDistance(estimate.distanceM) }}</span>
            </div>
            <div>
              <el-icon><Clock /></el-icon>
              <span>约 {{ formatTaxiDuration(estimate.durationMin) }}</span>
            </div>
            <span class="estimate-summary__mock">模拟估价</span>
          </div>

          <div v-if="estimate.vehicleOptions.length === 0" class="vehicle-empty">
            <span class="vehicle-empty__icon">
              <el-icon><WarningFilled /></el-icon>
            </span>
            <strong>暂无可用车型</strong>
            <p>本次估价没有返回可用车型，请重新估价后再试。</p>
            <el-button size="small" round @click="handleEstimate">
              重新估价
            </el-button>
          </div>

          <div v-else class="vehicle-options">
            <button
              v-for="option in estimate.vehicleOptions"
              :key="option.vehicleType"
              type="button"
              class="vehicle-option"
              :class="{
                'vehicle-option--active':
                  selectedVehicleType === option.vehicleType,
              }"
              @click="selectedVehicleType = option.vehicleType"
            >
              <span class="vehicle-option__icon">
                <el-icon><Van /></el-icon>
              </span>
              <span class="vehicle-option__name">{{ option.vehicleType }}</span>
              <span class="vehicle-option__fare">
                {{ formatFare(option.estimatedFare) }}
              </span>
              <span class="vehicle-option__radio" aria-hidden="true">
                <i v-if="selectedVehicleType === option.vehicleType" />
              </span>
            </button>
          </div>

          <template v-if="estimate.vehicleOptions.length > 0">
            <div class="estimate-action">
              <div class="estimate-action__copy">
                <strong>{{ selectedVehicle?.vehicleType || '请选择车型' }}</strong>
                <small>
                  {{ selectedVehicle ? formatFare(selectedVehicle.estimatedFare) : '--' }}
                  · 服务端重新计价
                </small>
              </div>
              <el-button
                type="primary"
                size="large"
                :icon="Van"
                :loading="creating"
                :disabled="!selectedVehicle"
                @click="handleCreate"
              >
                创建模拟订单
              </el-button>
            </div>

            <p class="estimate-footnote">
              创建后可在“我的订单”中查看；这是模拟订单，不会产生真实派单或扣费。
            </p>
          </template>
        </div>

        <div v-else class="estimate-empty">
          <span class="estimate-empty__icon">
            <el-icon><Money /></el-icon>
          </span>
          <strong>等待估价</strong>
          <p>选择上车点和目的地后，点击“开始模拟估价”。</p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.order-create-page {
  max-width: 1120px;
  margin: 0 auto;
}

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

.mock-notice__chip {
  flex: 0 0 auto;
  padding: 2px 7px;
  border: 1px solid #f0dfbe;
  border-radius: 5px;
  color: #a57417;
  font-size: 9px;
  background: #fff8ec;
}

.create-alert {
  border-radius: 12px;
}

.create-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.92fr);
  align-items: start;
  gap: 18px;
}

.route-form-card,
.estimate-card {
  padding: 21px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 17px;
}

.card-head h2 {
  color: var(--ta-ink);
  font-size: 16px;
  font-weight: 600;
}

.card-head p {
  margin-top: 3px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.65;
}

.card-head__icon {
  display: grid;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--ta-primary);
  font-size: 17px;
  place-items: center;
  background: #eaf4ff;
}

.route-form :deep(.el-form-item__label) {
  padding-bottom: 5px;
  color: var(--ta-ink-secondary);
  font-size: 12px;
  font-weight: 500;
}

.route-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 10px;
}

.route-field__marker {
  display: grid;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  margin-top: 3px;
  border-radius: 9px;
  font-size: 14px;
  place-items: center;
}

.route-field__marker--pickup {
  color: #2f7ef7;
  background: #eaf4ff;
}

.route-field__marker--destination {
  color: #22a978;
  background: #e9f8f1;
}

.route-field__body {
  flex: 1;
  min-width: 0;
}

.route-field__label {
  display: block;
  margin-bottom: 2px;
  color: var(--ta-ink-secondary);
  font-size: 11.5px;
  font-weight: 500;
}

.route-swap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 5px 0 5px 12px;
}

.route-swap__line {
  width: 1px;
  height: 12px;
  margin-left: 1px;
  background: repeating-linear-gradient(
    180deg,
    #c7dcf5 0 3px,
    transparent 3px 6px
  );
}

.estimate-button {
  width: 100%;
  margin-top: 20px;
  border-radius: 12px;
}

.route-form__note {
  margin-top: 12px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.7;
}

.estimate-mock-tag {
  flex: 0 0 auto;
  padding: 2px 7px;
  border: 1px solid #f0dfbe;
  border-radius: 5px;
  color: #a57417;
  font-size: 9px;
  background: #fff8ec;
}

.estimate-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 5px;
  padding: 46px 16px;
  text-align: center;
}

.estimate-state__icon {
  display: grid;
  width: 44px;
  height: 44px;
  margin-bottom: 5px;
  border-radius: 14px;
  color: #c9821d;
  font-size: 21px;
  place-items: center;
  background: #fff6e8;
}

.estimate-state strong {
  color: var(--ta-ink);
  font-size: 12.5px;
  font-weight: 600;
}

.estimate-state p {
  max-width: 290px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.7;
}

.estimate-state .el-button {
  margin-top: 5px;
}

.estimate-spinner {
  width: 28px;
  height: 28px;
  margin-bottom: 5px;
  border: 2px solid #dceafc;
  border-top-color: var(--ta-primary);
  border-radius: 50%;
  animation: estimate-spin 0.9s linear infinite;
}

.estimate-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px;
  border: 1px solid #e5effb;
  border-radius: 12px;
  background: #f8fcff;
}

.estimate-summary div {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ta-ink-secondary);
  font-size: 10.5px;
}

.estimate-summary div + div {
  padding-left: 10px;
  border-left: 1px solid #e3eefb;
}

.estimate-summary__mock {
  margin-left: auto;
  padding: 2px 6px;
  border-radius: 4px;
  color: #a57417;
  font-size: 8.5px;
  background: #fff6e8;
}

.vehicle-options {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 12px;
}

.vehicle-option {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid #e4eef9;
  border-radius: 11px;
  text-align: left;
  background: #fbfdff;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.vehicle-option:hover {
  border-color: #c6dcf7;
  background: #f6fbff;
}

.vehicle-option--active {
  border-color: #9cc7fb;
  background: #f2f8ff;
  box-shadow: inset 0 0 0 1px rgba(47, 126, 247, 0.12);
}

.vehicle-option__icon {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: var(--ta-primary);
  font-size: 15px;
  place-items: center;
  background: #eaf4ff;
}

.vehicle-option__name {
  flex: 1;
  min-width: 0;
  color: var(--ta-ink);
  font-size: 11.5px;
  font-weight: 500;
}

.vehicle-option__fare {
  color: var(--ta-primary-deep);
  font-size: 12px;
  font-weight: 600;
}

.vehicle-option__radio {
  display: grid;
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  margin-left: 2px;
  border: 1px solid #c7d8ec;
  border-radius: 50%;
  place-items: center;
  background: #fff;
}

.vehicle-option--active .vehicle-option__radio {
  border-color: var(--ta-primary);
}

.vehicle-option__radio i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ta-primary);
}

.estimate-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed #e8f1fb;
}

.estimate-action__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.estimate-action__copy strong {
  color: var(--ta-ink);
  font-size: 12.5px;
  font-weight: 600;
}

.estimate-action__copy small {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
}

.estimate-action :deep(.el-button) {
  flex: 0 0 auto;
  border-radius: 10px;
}

.estimate-footnote {
  margin-top: 10px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.7;
}

.estimate-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 5px;
  padding: 64px 16px;
  text-align: center;
}

.estimate-empty__icon {
  display: grid;
  width: 50px;
  height: 50px;
  margin-bottom: 6px;
  border-radius: 16px;
  color: var(--ta-primary);
  font-size: 24px;
  place-items: center;
  background: linear-gradient(145deg, #f1f8ff, #e1efff);
}

.estimate-empty strong {
  color: var(--ta-ink);
  font-size: 12.5px;
  font-weight: 600;
}

.estimate-empty p {
  max-width: 270px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.7;
}

.vehicle-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 5px;
  margin-top: 12px;
  padding: 28px 16px;
  border: 1px dashed #ead9ce;
  border-radius: 12px;
  text-align: center;
  background: #fffaf7;
}

.vehicle-empty__icon {
  display: grid;
  width: 38px;
  height: 38px;
  margin-bottom: 3px;
  border-radius: 12px;
  color: #b4775f;
  font-size: 18px;
  place-items: center;
  background: #fff1ea;
}

.vehicle-empty strong {
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
}

.vehicle-empty p {
  max-width: 280px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.7;
}

@keyframes estimate-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 960px) {
  .create-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .mock-notice {
    flex-wrap: wrap;
  }

  .mock-notice__chip {
    margin-left: 50px;
  }

  .route-form-card,
  .estimate-card {
    padding: 16px;
  }

  .estimate-action {
    align-items: stretch;
    flex-direction: column;
  }

  .estimate-action :deep(.el-button) {
    width: 100%;
  }
}
</style>
