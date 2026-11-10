<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  CircleCheck,
  Clock,
  Location,
  MapLocation,
  WarningFilled,
} from '@element-plus/icons-vue'
import AmapCanvas from '@/components/map/AmapCanvas.vue'
import { planRoute } from '@/api/amap'
import type { AmapPoi } from '@/types/amap'
import type { TripDraftDay, TripWarning } from '@/types/trip'
import {
  formatDayDate,
  getDayWarnings,
  getItemTypeLabel,
  getValidationLabel,
  getWarningText,
} from '@/utils/trip'
import { formatDuration } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    days: TripDraftDay[]
    warnings?: TripWarning[] | null
    validationStatus?: string | null
    initialDayIndex?: number
  }>(),
  {
    warnings: null,
    validationStatus: null,
    initialDayIndex: 1,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const activeDayIndex = ref(props.initialDayIndex)

const activeDay = computed(
  () =>
    props.days.find((day) => day.dayIndex === activeDayIndex.value) ??
    props.days[0] ??
    null,
)

const activeWarnings = computed(() =>
  activeDay.value
    ? getDayWarnings(props.warnings, activeDay.value.dayIndex)
    : [],
)

const validationLabel = computed(() =>
  getValidationLabel(props.validationStatus),
)

const totalDuration = computed(() =>
  (activeDay.value?.items ?? []).reduce(
    (total, item) => total + (item.durationMin ?? 0),
    0,
  ),
)

const isValidationDone = computed(
  () => props.validationStatus === 'done' && activeWarnings.value.length === 0,
)

watch(
  () => props.modelValue,
  (open) => {
    if (open && props.days.length > 0) {
      const hasInitial = props.days.some(
        (day) => day.dayIndex === props.initialDayIndex,
      )
      activeDayIndex.value = hasInitial
        ? props.initialDayIndex
        : props.days[0].dayIndex
    }
  },
)

watch(
  () => props.initialDayIndex,
  (dayIndex) => {
    if (props.modelValue) {
      activeDayIndex.value = dayIndex
    }
  },
)

function selectDay(dayIndex: number): void {
  activeDayIndex.value = dayIndex
}

function formatCoords(longitude: number | null, latitude: number | null): string {
  if (longitude === null || latitude === null) {
    return '未提供坐标'
  }
  return `${longitude.toFixed(6)}, ${latitude.toFixed(6)}`
}

const mapStops = computed<AmapPoi[]>(() =>
  (activeDay.value?.items ?? [])
    .filter(
      (item) => item.longitude !== null && item.latitude !== null,
    )
    .map((item) => ({
      id: item.localId,
      name: item.name,
      type: item.itemType,
      typeLabel: getItemTypeLabel(item.itemType),
      typecode: '',
      address: item.address,
      city: '',
      district: '',
      longitude: item.longitude as number,
      latitude: item.latitude as number,
      tel: '',
      rating: null,
      photoUrl: null,
      itemType:
        item.itemType === 'ATTRACTION' ||
        item.itemType === 'ACTIVITY' ||
        item.itemType === 'MEAL' ||
        item.itemType === 'HOTEL' ||
        item.itemType === 'TRANSPORT'
          ? item.itemType
          : 'OTHER',
    })),
)

const dayRoutePath = ref<Array<[number, number]>>([])
const dayRouteLoading = ref(false)
const dayRouteError = ref('')
const dayRouteDistanceM = ref(0)
const dayRouteDurationSec = ref(0)

let dayRouteRequestId = 0

const dayRouteSummary = computed(() => {
  if (dayRouteDistanceM.value <= 0) {
    return ''
  }
  const distance =
    dayRouteDistanceM.value < 1000
      ? `${Math.round(dayRouteDistanceM.value)} 米`
      : `${(dayRouteDistanceM.value / 1000).toFixed(1)} 公里`
  const minutes = Math.max(1, Math.ceil(dayRouteDurationSec.value / 60))
  const duration =
    minutes < 60
      ? `${minutes} 分钟`
      : `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
  return `驾车约 ${distance} · ${duration}`
})

function resetDayRoute(): void {
  dayRouteRequestId += 1
  dayRoutePath.value = []
  dayRouteDistanceM.value = 0
  dayRouteDurationSec.value = 0
  dayRouteLoading.value = false
  dayRouteError.value = ''
}

async function loadDayRoute(): Promise<void> {
  const stops = mapStops.value
  if (stops.length < 2) {
    resetDayRoute()
    return
  }

  const requestId = ++dayRouteRequestId
  dayRouteLoading.value = true
  dayRouteError.value = ''
  dayRoutePath.value = []
  dayRouteDistanceM.value = 0
  dayRouteDurationSec.value = 0

  try {
    const path: Array<[number, number]> = []
    let distanceM = 0
    let durationSec = 0

    for (let index = 1; index < stops.length; index += 1) {
      const from = stops[index - 1]
      const to = stops[index]
      const leg = await planRoute({
        mode: 'driving',
        origin: [from.longitude, from.latitude],
        destination: [to.longitude, to.latitude],
      })
      if (requestId !== dayRouteRequestId) {
        return
      }
      for (const point of leg.path) {
        const previous = path[path.length - 1]
        if (
          !previous ||
          previous[0] !== point[0] ||
          previous[1] !== point[1]
        ) {
          path.push(point)
        }
      }
      distanceM += leg.distanceM
      durationSec += leg.durationSec
    }

    dayRoutePath.value = path
    dayRouteDistanceM.value = distanceM
    dayRouteDurationSec.value = durationSec
  } catch (error) {
    if (requestId !== dayRouteRequestId) {
      return
    }
    dayRouteError.value =
      error instanceof Error ? error.message : '地图路线规划失败'
  } finally {
    if (requestId === dayRouteRequestId) {
      dayRouteLoading.value = false
    }
  }
}

watch(
  [() => props.modelValue, activeDayIndex],
  ([open]) => {
    if (open) {
      void loadDayRoute()
    } else {
      resetDayRoute()
    }
  },
  { immediate: true },
)

watch(
  () => props.days,
  () => {
    if (props.modelValue) {
      void loadDayRoute()
    }
  },
  { deep: false },
)
</script>

<template>
  <el-drawer
    v-model="visible"
    title="行程路线"
    direction="rtl"
    size="min(480px, 92vw)"
    append-to-body
  >
    <div class="trip-route">
      <div v-if="props.days.length > 1" class="route-day-tabs">
        <button
          v-for="day in props.days"
          :key="day.dayIndex"
          type="button"
          class="route-day-tab"
          :class="{ 'route-day-tab--active': day.dayIndex === activeDayIndex }"
          @click="selectDay(day.dayIndex)"
        >
          第{{ day.dayIndex }}天
        </button>
      </div>

      <template v-if="activeDay">
        <div class="route-map">
          <AmapCanvas
            v-if="props.modelValue && mapStops.length > 0"
            :pois="mapStops"
            :route-path="dayRoutePath"
            :route-start="mapStops[0] ?? null"
            :route-end="mapStops[mapStops.length - 1] ?? null"
          />
          <div v-else-if="props.modelValue" class="route-map__empty">
            <el-icon><MapLocation /></el-icon>
            当天没有可用坐标，无法在地图上展示
          </div>
          <span v-if="dayRouteLoading" class="route-map__loading">
            正在规划地图路线…
          </span>
        </div>

        <div v-if="dayRouteSummary" class="route-map__summary">
          <el-icon><MapLocation /></el-icon>
          {{ dayRouteSummary }}
        </div>
        <p v-else-if="dayRouteError" class="route-map__error">
          <span>{{ dayRouteError }}，以下按原顺序展示站点。</span>
          <el-button
            text
            size="small"
            :disabled="dayRouteLoading"
            @click="loadDayRoute"
          >
            {{ dayRouteLoading ? '重新规划中…' : '重试' }}
          </el-button>
        </p>

        <div class="route-summary">
          <div>
            <strong>第{{ activeDay.dayIndex }}天路线</strong>
            <span>{{ formatDayDate(activeDay.dayDate) }}</span>
          </div>
          <small>
            {{ activeDay.items.length }} 站
            <template v-if="totalDuration > 0">
              · 停留{{ formatDuration(totalDuration) }}
            </template>
          </small>
        </div>

        <div v-if="activeDay.items.length > 0" class="route-stops">
          <div
            v-for="(item, index) in activeDay.items"
            :key="item.localId"
            class="route-stop"
          >
            <div class="route-stop__rail">
              <span class="route-stop__dot">{{ index + 1 }}</span>
              <span
                v-if="index < activeDay.items.length - 1"
                class="route-stop__line"
              />
            </div>

            <div class="route-stop__body">
              <div class="route-stop__title">
                <span class="route-stop__time">
                  {{ item.timeSlot || '未指定' }}
                </span>
                <strong>{{ item.name }}</strong>
                <span class="route-stop__type">
                  {{ getItemTypeLabel(item.itemType) }}
                </span>
              </div>
              <p v-if="item.address" class="route-stop__meta">
                <el-icon><Location /></el-icon>
                {{ item.address }}
              </p>
              <p class="route-stop__meta">
                <el-icon><Clock /></el-icon>
                <template v-if="item.durationMin">
                  {{ formatDuration(item.durationMin) }}
                </template>
                <template v-else>停留时长未指定</template>
                <span class="route-stop__coords">
                  {{ formatCoords(item.longitude, item.latitude) }}
                </span>
              </p>
            </div>
          </div>
        </div>

        <div v-else class="route-empty">当天暂无安排的景点</div>

        <div
          v-if="activeWarnings.length"
          class="route-validation route-validation--warning"
        >
          <div class="route-validation__head">
            <el-icon><WarningFilled /></el-icon>
            <strong>路线校验提醒</strong>
          </div>
          <ul>
            <li
              v-for="(warning, index) in activeWarnings"
              :key="index"
            >
              {{ getWarningText(warning) }}
            </li>
          </ul>
        </div>

        <div
          v-else-if="isValidationDone"
          class="route-validation route-validation--success"
        >
          <div class="route-validation__head">
            <el-icon><CircleCheck /></el-icon>
            <strong>{{ validationLabel }}</strong>
          </div>
          <p>当日未发现明显路线问题，出行前仍建议确认交通与开放时间。</p>
        </div>

        <div v-else class="route-validation">
          <div class="route-validation__head">
            <el-icon><MapLocation /></el-icon>
            <strong>{{ validationLabel }}</strong>
          </div>
          <p>
            当前行程没有可用的路线校验结果。手动修改行程不会调用 Agent，
            路线将在后续阶段重新校验。
          </p>
        </div>

        <p class="route-footnote">
          地图与驾车路线由高德提供；没有经纬度的站点只出现在路线列表中。
        </p>
      </template>

      <div v-else class="route-empty">该行程暂无可展示的每日路线</div>
    </div>
  </el-drawer>
</template>

<style scoped>
.trip-route {
  padding-bottom: 12px;
}

.route-day-tabs {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.route-day-tab {
  padding: 5px 11px;
  border: 1px solid var(--ta-border);
  border-radius: 999px;
  color: var(--ta-ink-secondary);
  font-size: 11px;
  background: #fff;
  cursor: pointer;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.route-day-tab:hover {
  color: var(--ta-primary);
  border-color: #bcd7fb;
}

.route-day-tab--active {
  color: #fff;
  border-color: var(--ta-primary);
  background: var(--ta-primary);
}

.route-map {
  position: relative;
  overflow: hidden;
  height: 220px;
  border: 1px solid #dce9e5;
  border-radius: 14px;
  background: #edf5ee;
}

.route-map__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  height: 100%;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  text-align: center;
}

.route-map__empty .el-icon {
  color: var(--ta-primary);
  font-size: 22px;
}

.route-map__loading {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 6;
  padding: 4px 8px;
  border: 1px solid rgba(220, 234, 248, 0.96);
  border-radius: 999px;
  color: var(--ta-ink-secondary);
  font-size: 9.5px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 4px 12px rgba(41, 87, 145, 0.1);
}

.route-map__summary {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  color: var(--ta-primary-deep);
  font-size: 10px;
}

.route-map__error {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  color: #b46a5a;
  font-size: 10px;
  line-height: 1.6;
}

.route-summary {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  margin-top: 18px;
}

.route-summary > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.route-summary strong {
  color: var(--ta-ink);
  font-size: 14px;
  font-weight: 600;
}

.route-summary span,
.route-summary small {
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.route-stops {
  margin-top: 12px;
}

.route-stop {
  display: flex;
  gap: 10px;
}

.route-stop__rail {
  display: flex;
  align-items: center;
  flex-direction: column;
  flex: 0 0 auto;
  width: 22px;
}

.route-stop__dot {
  display: grid;
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border: 1px solid #cfe2f8;
  border-radius: 50%;
  color: var(--ta-primary);
  font-size: 9px;
  font-weight: 600;
  place-items: center;
  background: #f3f9ff;
}

.route-stop__line {
  flex: 1;
  width: 1px;
  min-height: 24px;
  margin: 5px 0;
  background: repeating-linear-gradient(
    180deg,
    #c7dcf5 0 4px,
    transparent 4px 8px
  );
}

.route-stop__body {
  flex: 1;
  min-width: 0;
  padding: 1px 0 18px;
}

.route-stop__title {
  display: flex;
  align-items: baseline;
  gap: 7px;
  flex-wrap: wrap;
}

.route-stop__time {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 4px;
  color: var(--ta-primary);
  font-size: 9px;
  background: #eaf4ff;
}

.route-stop__title strong {
  color: var(--ta-ink);
  font-size: 12.5px;
  font-weight: 600;
}

.route-stop__type {
  padding: 1px 5px;
  border-radius: 4px;
  color: #7189a5;
  font-size: 8.5px;
  background: #f1f6fc;
}

.route-stop__meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 4px;
  color: var(--ta-ink-muted);
  font-size: 10px;
  line-height: 1.6;
}

.route-stop__coords {
  margin-left: 4px;
  color: #a6b7cb;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
}

.route-empty {
  margin-top: 16px;
  padding: 30px 16px;
  border: 1px dashed #dce9f8;
  border-radius: 12px;
  color: var(--ta-ink-muted);
  font-size: 11px;
  text-align: center;
}

.route-validation {
  margin-top: 8px;
  padding: 11px 12px;
  border: 1px solid #dcebfb;
  border-radius: 11px;
  color: var(--ta-ink-secondary);
  background: #f4f9ff;
}

.route-validation--warning {
  border-color: #f5dfbb;
  color: #8b6a31;
  background: #fffaf1;
}

.route-validation--success {
  border-color: #d9efe4;
  color: #377a60;
  background: #f5fcf8;
}

.route-validation__head {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
}

.route-validation__head strong {
  font-weight: 600;
}

.route-validation p {
  margin-top: 4px;
  font-size: 10.5px;
  line-height: 1.7;
}

.route-validation ul {
  margin: 5px 0 0;
  padding-left: 18px;
  font-size: 10.5px;
  line-height: 1.7;
}

.route-footnote {
  margin-top: 14px;
  color: var(--ta-ink-muted);
  font-size: 10px;
  line-height: 1.7;
  text-align: center;
}
</style>
