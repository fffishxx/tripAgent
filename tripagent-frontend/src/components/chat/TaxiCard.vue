<script setup lang="ts">
import { computed } from 'vue'
import {
  CircleCheck,
  Location,
  Van,
  WarningFilled,
} from '@element-plus/icons-vue'
import type {
  AgentTaxiCard,
  AgentTaxiVehicleOption,
} from '@/types/agent'
import { formatFare } from '@/utils/format'

const props = defineProps<{
  card: AgentTaxiCard
  disabled?: boolean
  selectedVehicle?: string | null
}>()

const emit = defineEmits<{
  select: [option: AgentTaxiVehicleOption]
}>()

const options = computed(() => props.card.vehicle_options ?? [])
const isSelecting = computed(
  () => props.card.status === 'SELECT_VEHICLE' && options.value.length > 0,
)
const isCreated = computed(() => props.card.status === 'CREATED')

const statusText = computed(
  () =>
    props.card.status_text ||
    ({
      SELECT_VEHICLE: '请选择车型',
      CREATED: '订单已创建',
      CANCELLED: '订单已取消',
      COMPLETED: '模拟完成',
    }[props.card.status ?? ''] ??
      '模拟打车'),
)

const distanceText = computed(() => {
  const distance = props.card.distance_km
  return typeof distance === 'number' ? `${distance} 公里` : ''
})

const durationText = computed(() => {
  const duration = props.card.duration_min
  return typeof duration === 'number' ? `约 ${duration} 分钟` : ''
})

function optionPrice(option: AgentTaxiVehicleOption): string {
  return formatFare(option.coupon_price ?? option.price)
}

function handleSelect(option: AgentTaxiVehicleOption): void {
  if (props.disabled || props.selectedVehicle || !isSelecting.value) {
    return
  }
  emit('select', option)
}
</script>

<template>
  <article class="taxi-card">
    <header class="taxi-card__head">
      <span class="taxi-card__icon">
        <el-icon><Van /></el-icon>
      </span>
      <div class="taxi-card__title">
        <div class="taxi-card__title-row">
          <strong>打车出行</strong>
          <span class="taxi-card__mock">模拟</span>
          <span
            class="taxi-card__status"
            :class="{ 'taxi-card__status--success': isCreated }"
          >
            {{ statusText }}
          </span>
        </div>
        <small>{{ card.provider_name || 'Java 模拟打车' }}</small>
      </div>
    </header>

    <div class="taxi-route">
      <div class="taxi-route__point">
        <span class="taxi-route__dot taxi-route__dot--pickup" />
        <span>
          <small>上车点</small>
          <strong>{{ card.pickup || '待确认' }}</strong>
        </span>
      </div>
      <span class="taxi-route__line" aria-hidden="true" />
      <div class="taxi-route__point">
        <span class="taxi-route__dot taxi-route__dot--destination" />
        <span>
          <small>目的地</small>
          <strong>{{ card.destination || '待确认' }}</strong>
        </span>
      </div>
    </div>

    <div v-if="distanceText || durationText" class="taxi-card__meta">
      <span v-if="distanceText">
        <el-icon><Location /></el-icon>
        {{ distanceText }}
      </span>
      <span v-if="durationText">{{ durationText }}</span>
      <span v-if="card.city">{{ card.city }}</span>
    </div>

    <div v-if="isSelecting" class="taxi-options">
      <div class="taxi-options__head">
        <strong>选择车型</strong>
        <span>回复车型名称，由助手创建模拟订单</span>
      </div>

      <button
        v-for="option in options"
        :key="option.name"
        type="button"
        class="taxi-option"
        :class="{ 'taxi-option--selected': selectedVehicle === option.name }"
        :disabled="disabled || Boolean(selectedVehicle)"
        @click="handleSelect(option)"
      >
        <span class="taxi-option__name">{{ option.name }}</span>
        <span class="taxi-option__price">{{ optionPrice(option) }}</span>
        <span class="taxi-option__action">
          {{ selectedVehicle === option.name ? '已选择' : '选择' }}
        </span>
      </button>

      <p v-if="selectedVehicle" class="taxi-options__selected">
        <el-icon><CircleCheck /></el-icon>
        已发送车型选择，等待助手确认订单。
      </p>
    </div>

    <div v-else-if="isCreated" class="taxi-order-summary">
      <div>
        <small>车型</small>
        <strong>{{ card.vehicle_type || '模拟车型' }}</strong>
      </div>
      <div>
        <small>预估车费</small>
        <strong>{{ formatFare(card.estimated_fare) }}</strong>
      </div>
      <div>
        <small>订单号</small>
        <strong class="taxi-order-summary__no">
          {{ card.order_id || card.java_order_id || '--' }}
        </strong>
      </div>
    </div>

    <p class="taxi-card__note">
      <el-icon><WarningFilled /></el-icon>
      {{
        card.note ||
        '本项目仅支持模拟打车，不接入真实支付，也不会向真实打车平台派单。'
      }}
    </p>
  </article>
</template>

<style scoped>
.taxi-card {
  margin-top: 12px;
  padding: 14px;
  border: 1px solid #e1edfa;
  border-radius: 15px;
  background:
    linear-gradient(150deg, rgba(247, 251, 255, 0.98), rgba(255, 255, 255, 0.99)),
    #fff;
}

.taxi-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.taxi-card__icon {
  display: grid;
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  color: var(--ta-primary);
  font-size: 18px;
  place-items: center;
  background: #eaf4ff;
}

.taxi-card__title {
  min-width: 0;
}

.taxi-card__title-row {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.taxi-card__title-row strong {
  color: var(--ta-ink-strong);
  font-size: 14px;
  font-weight: 600;
}

.taxi-card__mock {
  padding: 1px 6px;
  border: 1px solid #f2d9ad;
  border-radius: 5px;
  color: #a57417;
  font-size: 9px;
  font-weight: 600;
  background: #fff6e8;
}

.taxi-card__status {
  color: var(--ta-primary);
  font-size: 10px;
}

.taxi-card__status--success {
  color: #1c8b60;
}

.taxi-card__title small {
  display: block;
  margin-top: 1px;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.taxi-route {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #edf4fc;
  border-radius: 11px;
  background: #fbfdff;
}

.taxi-route__point {
  display: flex;
  flex: 1;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

.taxi-route__point > span:last-child {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.taxi-route__point small {
  color: var(--ta-ink-muted);
  font-size: 9px;
}

.taxi-route__point strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 11.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.taxi-route__dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  margin-top: 6px;
  border-radius: 50%;
}

.taxi-route__dot--pickup {
  background: #2f7ef7;
  box-shadow: 0 0 0 3px rgba(47, 126, 247, 0.14);
}

.taxi-route__dot--destination {
  background: #25b07a;
  box-shadow: 0 0 0 3px rgba(37, 176, 122, 0.14);
}

.taxi-route__line {
  flex: 0 0 auto;
  width: 26px;
  height: 1px;
  margin-top: 9px;
  background-image: linear-gradient(90deg, #bcd5f3 55%, transparent 55%);
  background-size: 7px 1px;
}

.taxi-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 9px;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.taxi-card__meta span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.taxi-options {
  margin-top: 12px;
}

.taxi-options__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 7px;
}

.taxi-options__head strong {
  color: var(--ta-ink);
  font-size: 11.5px;
  font-weight: 600;
}

.taxi-options__head span {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
}

.taxi-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 11px;
  border: 1px solid #e7eff9;
  border-radius: 10px;
  text-align: left;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.taxi-option + .taxi-option {
  margin-top: 6px;
}

.taxi-option:hover:not(:disabled) {
  border-color: #bcd7fb;
  background: #f8fbff;
  box-shadow: 0 6px 14px rgba(47, 126, 247, 0.08);
}

.taxi-option:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.taxi-option--selected {
  border-color: var(--ta-primary);
  background: #f2f8ff;
  opacity: 1;
}

.taxi-option__name {
  flex: 1;
  min-width: 0;
  color: var(--ta-ink);
  font-size: 11.5px;
  font-weight: 500;
}

.taxi-option__price {
  color: var(--ta-primary-deep);
  font-size: 12px;
  font-weight: 600;
}

.taxi-option__action {
  min-width: 44px;
  padding: 2px 8px;
  border-radius: 999px;
  color: var(--ta-primary);
  font-size: 9.5px;
  text-align: center;
  background: #eaf4ff;
}

.taxi-options__selected {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  color: #21885f;
  font-size: 10px;
}

.taxi-order-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 11px;
  padding: 10px;
  border: 1px solid #edf4fc;
  border-radius: 10px;
  background: #fbfdff;
}

.taxi-order-summary > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.taxi-order-summary small {
  color: var(--ta-ink-muted);
  font-size: 9px;
}

.taxi-order-summary strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.taxi-order-summary__no {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9.5px !important;
}

.taxi-card__note {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-top: 10px;
  color: #9a7a41;
  font-size: 9.5px;
  line-height: 1.6;
}

.taxi-card__note .el-icon {
  flex: 0 0 auto;
  margin-top: 1px;
}

@media (max-width: 720px) {
  .taxi-route {
    flex-direction: column;
    align-items: stretch;
    gap: 7px;
  }

  .taxi-route__line {
    width: 1px;
    height: 12px;
    margin: 0 0 0 3px;
    background-image: linear-gradient(180deg, #bcd5f3 55%, transparent 55%);
    background-size: 1px 6px;
  }

  .taxi-order-summary {
    grid-template-columns: 1fr;
  }
}
</style>
