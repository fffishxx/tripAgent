<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Calendar,
  CircleCheck,
  Food,
  House,
  Location,
  User,
  Wallet,
  WarningFilled,
} from '@element-plus/icons-vue'
import type {
  AgentRouteWarning,
  AgentTripDay,
  AgentTripItem,
  AgentTripPlan,
  AgentValidationResult,
} from '@/types/agent'
import { formatDuration } from '@/utils/format'

const props = defineProps<{
  plan: AgentTripPlan
  validation?: AgentValidationResult | null
}>()

const CROWD_LABELS: Record<string, string> = {
  general: '通用人群',
  family: '家庭出行',
  couple: '情侣出行',
  elderly: '长辈出行',
  solo: '独自出行',
  student: '学生出行',
}

const days = computed(() => props.plan.days ?? [])
const activeDays = ref<string[]>(
  days.value.length > 0 ? [`day-${days.value[0].day_index}`] : [],
)

const daysCount = computed(
  () => props.plan.assumptions?.days || days.value.length || 0,
)
const crowdLabel = computed(
  () => CROWD_LABELS[props.plan.assumptions?.crowd ?? ''] || '出行人群未指定',
)
const budgetTotal = computed(
  () => props.plan.budget_estimate?.total || '预算灵活',
)

const warnings = computed<AgentRouteWarning[]>(() => {
  if (props.validation?.warnings?.length) {
    return props.validation.warnings
  }
  return props.plan.warnings ?? []
})

const validationError = computed(() => props.validation?.error || '')

const validationState = computed(() => {
  if (warnings.value.length > 0) {
    return { label: '路线建议调整', tone: 'warning' }
  }
  if (
    props.validation?.validation_status === 'done' ||
    props.plan.validation_status === 'done'
  ) {
    return { label: '路线已校验', tone: 'success' }
  }
  return { label: '路线校验中', tone: 'pending' }
})

const hasValidationIssue = computed(
  () => Boolean(validationError.value) || warnings.value.length > 0,
)

const showValidation = computed(
  () =>
    Boolean(validationError.value) ||
    warnings.value.length > 0 ||
    Boolean(props.validation) ||
    props.plan.validation_status === 'done',
)

const diningPreview = computed(() =>
  (props.plan.dining_suggestions ?? []).slice(0, 2).filter((item) => item.area),
)

function getItems(day: AgentTripDay): AgentTripItem[] {
  return Array.isArray(day.items) ? day.items : []
}

function getWarningMessage(warning: AgentRouteWarning): string {
  if (warning.message) {
    return warning.message
  }
  const route = [warning.from, warning.to].filter(Boolean).join(' → ')
  if (route && warning.walk_min) {
    return `${route} 步行约 ${warning.walk_min} 分钟，建议调整`
  }
  if (route) {
    return `${route} 路线可能不够顺畅，建议调整`
  }
  return '路线校验发现潜在问题，建议调整安排'
}
</script>

<template>
  <article class="trip-plan">
    <header class="trip-plan__head">
      <span class="trip-plan__icon">
        <el-icon><Location /></el-icon>
      </span>
      <div class="trip-plan__title">
        <div class="trip-plan__title-row">
          <strong>{{ plan.destination || '旅行' }}行程</strong>
          <span
            class="trip-plan__status"
            :class="`trip-plan__status--${validationState.tone}`"
          >
            {{ validationState.label }}
          </span>
        </div>
        <small>AI 生成 · 每日安排与路线建议</small>
      </div>
    </header>

    <div class="trip-plan__meta">
      <div class="trip-plan__metric">
        <span class="trip-plan__metric-icon"><el-icon><Calendar /></el-icon></span>
        <span><strong>{{ daysCount }} 天</strong><small>行程天数</small></span>
      </div>
      <div class="trip-plan__metric">
        <span class="trip-plan__metric-icon"><el-icon><User /></el-icon></span>
        <span><strong>{{ crowdLabel }}</strong><small>出行人群</small></span>
      </div>
      <div class="trip-plan__metric">
        <span class="trip-plan__metric-icon"><el-icon><Wallet /></el-icon></span>
        <span><strong>{{ budgetTotal }}</strong><small>预算范围</small></span>
      </div>
    </div>

    <el-collapse v-if="days.length" v-model="activeDays" class="trip-days">
      <el-collapse-item
        v-for="day in days"
        :key="day.day_index"
        :name="`day-${day.day_index}`"
      >
        <template #title>
          <span class="day-title">
            <em>第{{ day.day_index }}天</em>
            <strong>{{ day.theme || '当日行程' }}</strong>
            <small>{{ getItems(day).length }} 个安排</small>
          </span>
        </template>

        <div class="day-items">
          <div
            v-for="(item, index) in getItems(day)"
            :key="`${day.day_index}-${index}`"
            class="day-item"
          >
            <span class="day-item__time">{{ item.time_slot || '时段待定' }}</span>
            <div class="day-item__main">
              <div class="day-item__name-row">
                <strong>{{ item.poi || '未命名安排' }}</strong>
                <small v-if="item.duration_min">
                  {{ formatDuration(item.duration_min) }}
                </small>
              </div>
              <p v-if="item.reason">{{ item.reason }}</p>
              <p v-if="item.address" class="day-item__address">
                <el-icon><Location /></el-icon>
                {{ item.address }}
              </p>
            </div>
          </div>

          <div v-if="getItems(day).length === 0" class="day-empty">
            当天为自由活动，暂无固定安排
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>

    <div v-else class="trip-plan__empty">行程暂未包含每日安排</div>

    <div
      v-if="showValidation"
      class="route-check"
      :class="{
        'route-check--warning': hasValidationIssue,
        'route-check--success': !hasValidationIssue,
      }"
    >
      <div class="route-check__head">
        <el-icon>
          <WarningFilled v-if="hasValidationIssue" />
          <CircleCheck v-else />
        </el-icon>
        <strong>{{ hasValidationIssue ? '路线校验提醒' : '路线校验完成' }}</strong>
      </div>
      <p v-if="validationError" class="route-check__error">{{ validationError }}</p>
      <ul v-if="warnings.length" class="route-check__list">
        <li v-for="(warning, index) in warnings" :key="index">
          {{ getWarningMessage(warning) }}
        </li>
      </ul>
      <p v-else-if="!validationError" class="route-check__ok">
        暂未发现明显路线问题，出行前仍建议确认交通与开放时间。
      </p>
    </div>

    <div
      v-if="plan.lodging_area?.name || diningPreview.length"
      class="trip-plan__extra"
    >
      <div v-if="plan.lodging_area?.name" class="trip-plan__extra-item">
        <span class="trip-plan__extra-label">
          <el-icon><House /></el-icon>
          住宿
        </span>
        <span>{{ plan.lodging_area.name }}</span>
      </div>
      <div v-if="diningPreview.length" class="trip-plan__extra-item">
        <span class="trip-plan__extra-label">
          <el-icon><Food /></el-icon>
          美食
        </span>
        <span
          v-for="(item, index) in diningPreview"
          :key="item.area ?? `dining-${index}`"
        >
          {{ item.area }}<template v-if="item.cuisine"> · {{ item.cuisine }}</template>
        </span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.trip-plan {
  margin-top: 12px;
  padding: 16px;
  border: 1px solid #e1edfa;
  border-radius: 16px;
  background:
    linear-gradient(160deg, rgba(247, 251, 255, 0.96), rgba(255, 255, 255, 0.98)),
    #fff;
}

.trip-plan__head {
  display: flex;
  align-items: center;
  gap: 11px;
}

.trip-plan__icon {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border: 1px solid rgba(47, 126, 247, 0.12);
  border-radius: 12px;
  color: var(--ta-primary);
  font-size: 19px;
  place-items: center;
  background: #eaf4ff;
}

.trip-plan__title {
  min-width: 0;
}

.trip-plan__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.trip-plan__title-row strong {
  color: var(--ta-ink-strong);
  font-size: 15px;
  font-weight: 600;
}

.trip-plan__title small {
  display: block;
  margin-top: 1px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.trip-plan__status {
  display: inline-flex;
  align-items: center;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 500;
  white-space: nowrap;
}

.trip-plan__status--success {
  color: #13845a;
  background: #e8f8f1;
}

.trip-plan__status--warning {
  color: #b57411;
  background: #fff4e2;
}

.trip-plan__status--pending {
  color: #5478a6;
  background: #edf5ff;
}

.trip-plan__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 13px;
  padding: 10px;
  border: 1px solid #eef5fc;
  border-radius: 12px;
  background: #fbfdff;
}

.trip-plan__metric {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 2px 6px;
}

.trip-plan__metric + .trip-plan__metric {
  border-left: 1px solid #eef4fb;
}

.trip-plan__metric-icon {
  display: grid;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: var(--ta-primary);
  font-size: 14px;
  place-items: center;
  background: #edf5ff;
}

.trip-plan__metric > span:last-child {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.3;
}

.trip-plan__metric strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 11.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-plan__metric small {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
}

.trip-days {
  margin-top: 10px;
  border-top: 0;
  border-bottom: 0;
}

.trip-days :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 42px;
  padding: 9px 2px;
  border-bottom: 1px solid #eef5fc;
  background: transparent;
  line-height: 1.4;
}

.trip-days :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
  background: transparent;
}

.trip-days :deep(.el-collapse-item:last-child .el-collapse-item__header) {
  border-bottom: 0;
}

.trip-days :deep(.el-collapse-item__arrow) {
  color: #9bb0ca;
  font-size: 12px;
}

.day-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding-right: 8px;
}

.day-title em {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 6px;
  color: var(--ta-primary);
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
  background: #eaf4ff;
}

.day-title strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.day-title small {
  flex: 0 0 auto;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.day-items {
  padding: 4px 2px 6px;
}

.day-item {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px dashed #eef4fb;
}

.day-item:last-child {
  border-bottom: 0;
}

.day-item__time {
  align-self: flex-start;
  padding: 3px 0;
  color: var(--ta-primary);
  font-size: 10px;
  font-weight: 600;
}

.day-item__main {
  min-width: 0;
}

.day-item__name-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.day-item__name-row strong {
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
}

.day-item__name-row small {
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.day-item__main p {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 2px;
  color: var(--ta-ink-secondary);
  font-size: 11px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.day-item__main .day-item__address {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--ta-ink-muted);
  font-size: 10px;
  -webkit-line-clamp: 1;
}

.day-empty {
  padding: 12px 0;
  color: var(--ta-ink-muted);
  font-size: 11px;
  text-align: center;
}

.trip-plan__empty {
  margin-top: 12px;
  padding: 14px;
  border-radius: 10px;
  color: var(--ta-ink-muted);
  font-size: 11px;
  text-align: center;
  background: #f7faff;
}

.route-check {
  margin-top: 10px;
  padding: 11px 12px;
  border: 1px solid #dcebfb;
  border-radius: 11px;
  background: #f4f9ff;
}

.route-check--warning {
  border-color: #f6e1bd;
  background: #fffaf1;
}

.route-check__head {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ta-primary);
  font-size: 11px;
}

.route-check--warning .route-check__head {
  color: #b77913;
}

.route-check--success .route-check__head {
  color: #21885f;
}

.route-check__head strong {
  font-weight: 600;
}

.route-check__list {
  margin: 5px 0 0;
  padding-left: 17px;
  color: #8a672a;
  font-size: 11px;
  line-height: 1.7;
}

.route-check__error {
  margin-top: 4px;
  color: #a86018;
  font-size: 11px;
}

.route-check__ok {
  margin-top: 4px;
  color: #5b8a76;
  font-size: 11px;
  line-height: 1.6;
}

.trip-plan__extra {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.trip-plan__extra-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  padding: 5px 9px;
  border: 1px solid #edf4fc;
  border-radius: 8px;
  color: var(--ta-ink-secondary);
  font-size: 10px;
  background: #fbfdff;
}

.trip-plan__extra-item > span:not(.trip-plan__extra-label) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-plan__extra-label {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--ta-ink-muted);
  white-space: nowrap;
}

@media (max-width: 720px) {
  .trip-plan {
    padding: 12px;
  }

  .trip-plan__meta {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .trip-plan__metric + .trip-plan__metric {
    border-left: 0;
    border-top: 1px solid #eef4fb;
    padding-top: 7px;
  }

  .day-item {
    grid-template-columns: 48px minmax(0, 1fr);
  }
}
</style>
