<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Calendar, CircleCheck, Plus } from '@element-plus/icons-vue'
import {
  getAllTrips,
  getTripDetail,
  updateTrip,
} from '@/api/trips'
import type { AmapPoi } from '@/types/amap'
import type { TripDetail, TripSummary } from '@/types/trip'
import { ApiError } from '@/utils/request'
import {
  buildUpdateTripPayload,
  createDraftItem,
  createTripDraftFromDetail,
} from '@/utils/trip-draft'
import {
  formatDayDate,
  formatTripDateRange,
  TIME_SLOT_OPTIONS,
} from '@/utils/trip'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    poi?: AmapPoi | null
  }>(),
  {
    poi: null,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  added: [result: { tripId: string; tripTitle: string; dayIndex: number }]
}>()

const router = useRouter()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const trips = ref<TripSummary[]>([])
const tripsLoading = ref(false)
const tripsError = ref('')
const selectedTripId = ref('')
const detail = ref<TripDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const selectedDayIndex = ref<number | null>(null)
const timeSlot = ref('上午')
const submitting = ref(false)

const dayOptions = computed(() =>
  (detail.value?.plan.days ?? []).map((day) => ({
    value: day.dayIndex,
    label: `第${day.dayIndex}天 · ${formatDayDate(day.dayDate)}${
      day.theme ? ` · ${day.theme}` : ''
    }`,
    itemCount: day.items.length,
  })),
)

const selectedDay = computed(() =>
  detail.value?.plan.days.find(
    (day) => day.dayIndex === selectedDayIndex.value,
  ),
)

const selectedDayFull = computed(
  () => (selectedDay.value?.items.length ?? 0) >= 20,
)

const canSubmit = computed(
  () =>
    Boolean(props.poi && selectedTripId.value && selectedDay.value) &&
    !selectedDayFull.value &&
    !submitting.value &&
    !detailLoading.value,
)

const tripOptions = computed(() =>
  trips.value.map((trip) => ({
    value: trip.id,
    label: `${trip.title} · ${formatTripDateRange(
      trip.startDate,
      trip.endDate,
    )}`,
  })),
)

function resetState(): void {
  trips.value = []
  tripsError.value = ''
  selectedTripId.value = ''
  detail.value = null
  detailError.value = ''
  selectedDayIndex.value = null
  timeSlot.value = '上午'
  submitting.value = false
}

async function loadTrips(): Promise<void> {
  tripsLoading.value = true
  tripsError.value = ''
  try {
    trips.value = await getAllTrips()
    if (trips.value.length > 0) {
      selectedTripId.value = trips.value[0].id
      await loadTripDetail()
    }
  } catch (error) {
    tripsError.value =
      error instanceof Error ? error.message : '行程列表加载失败'
  } finally {
    tripsLoading.value = false
  }
}

async function loadTripDetail(): Promise<void> {
  if (!selectedTripId.value) {
    detail.value = null
    selectedDayIndex.value = null
    return
  }

  detailLoading.value = true
  detailError.value = ''
  try {
    const loaded = await getTripDetail(selectedTripId.value)
    detail.value = loaded
    const firstDay = loaded.plan.days[0]
    selectedDayIndex.value = firstDay?.dayIndex ?? null
  } catch (error) {
    detail.value = null
    selectedDayIndex.value = null
    detailError.value =
      error instanceof Error ? error.message : '行程详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function handleTripChange(): void {
  void loadTripDetail()
}

async function handleSubmit(): Promise<void> {
  if (!props.poi || !selectedTripId.value || !selectedDay.value || submitting.value) {
    return
  }

  submitting.value = true
  try {
    // 保存前重新读取，避免使用过期 version。
    const latest = await getTripDetail(selectedTripId.value)
    const targetDay = latest.plan.days.find(
      (day) => day.dayIndex === selectedDayIndex.value,
    )
    if (!targetDay) {
      ElMessage.error('所选日期已不存在，请重新选择')
      return
    }
    if (targetDay.items.length >= 20) {
      ElMessage.warning('该日期安排已达上限（20 个）')
      return
    }

    const draft = createTripDraftFromDetail(latest)
    const draftDay = draft.days.find(
      (day) => day.dayIndex === targetDay.dayIndex,
    )
    if (!draftDay) {
      ElMessage.error('所选日期已不存在，请重新选择')
      return
    }

    draftDay.items.push(
      createDraftItem({
        itemType: props.poi.itemType,
        name: props.poi.name,
        address: props.poi.address,
        longitude: props.poi.longitude,
        latitude: props.poi.latitude,
        timeSlot: timeSlot.value,
        durationMin: 60,
        reason: '通过地图探索加入',
        externalPoiId: props.poi.id,
      }),
    )

    const updated = await updateTrip(
      latest.id,
      buildUpdateTripPayload(latest, draft),
    )

    emit('added', {
      tripId: updated.id,
      tripTitle: updated.title,
      dayIndex: targetDay.dayIndex,
    })
    ElMessage.success(
      `已将「${props.poi.name}」加入${updated.title}第${targetDay.dayIndex}天`,
    )
    visible.value = false
  } catch (error) {
    if (
      error instanceof ApiError &&
      (error.code === 40902 || error.status === 409)
    ) {
      ElMessage.error('行程已被其他操作更新，请刷新后重试')
      await loadTripDetail()
      return
    }
    ElMessage.error(
      error instanceof Error && error.message
        ? error.message
        : '加入行程失败，请稍后重试',
    )
  } finally {
    submitting.value = false
  }
}

function goCreateTrip(): void {
  visible.value = false
  void router.push({ name: 'trip-create' })
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetState()
      nextTick(() => {
        void loadTrips()
      })
    }
  },
)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="加入已有行程"
    width="min(560px, 94vw)"
    :close-on-click-modal="false"
    append-to-body
  >
    <div v-if="poi" class="add-trip-poi">
      <span class="add-trip-poi__icon">
        <el-icon><Plus /></el-icon>
      </span>
      <div>
        <strong>{{ poi.name }}</strong>
        <p>{{ poi.address || `${poi.city}${poi.district}` || '景点地址待补充' }}</p>
      </div>
    </div>

    <div v-if="tripsLoading" class="add-trip-state">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else-if="tripsError" class="add-trip-state">
      <p>{{ tripsError }}</p>
      <el-button round size="small" @click="loadTrips">重新加载</el-button>
    </div>

    <div v-else-if="trips.length === 0" class="add-trip-empty">
      <span class="add-trip-empty__icon">
        <el-icon><Calendar /></el-icon>
      </span>
      <strong>还没有可加入的行程</strong>
      <p>先创建一条行程，或让 AI 生成行程后再回来添加景点。</p>
      <el-button type="primary" round size="small" @click="goCreateTrip">
        去创建行程
      </el-button>
    </div>

    <template v-else>
      <el-form label-position="top" class="add-trip-form">
        <el-form-item label="选择行程">
          <el-select
            v-model="selectedTripId"
            filterable
            class="add-trip-control"
            placeholder="请选择行程"
            @change="handleTripChange"
          >
            <el-option
              v-for="option in tripOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <div v-if="detailLoading" class="add-trip-detail-loading">
          <el-skeleton :rows="2" animated />
        </div>
        <div v-else-if="detailError" class="add-trip-detail-error">
          <span>{{ detailError }}</span>
          <el-button text size="small" @click="loadTripDetail">
            重试
          </el-button>
        </div>
        <template v-else>
          <div class="add-trip-form__grid">
            <el-form-item label="加入日期">
              <el-select
                v-model="selectedDayIndex"
                class="add-trip-control"
                placeholder="请选择日期"
              >
                <el-option
                  v-for="option in dayOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="时间段">
              <el-select v-model="timeSlot" class="add-trip-control">
                <el-option
                  v-for="slot in TIME_SLOT_OPTIONS"
                  :key="slot"
                  :label="slot"
                  :value="slot"
                />
              </el-select>
            </el-form-item>
          </div>

          <div v-if="selectedDay" class="add-trip-day-hint">
            <el-icon><CircleCheck /></el-icon>
            当天已有 {{ selectedDay.items.length }} 个安排
            <span v-if="selectedDayFull" class="add-trip-day-hint__limit">
              已达 20 个上限
            </span>
          </div>
        </template>
      </el-form>
    </template>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="handleSubmit"
      >
        加入行程
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.add-trip-poi {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 18px;
  padding: 11px 12px;
  border: 1px solid #e2eefb;
  border-radius: 12px;
  background: #f8fcff;
}

.add-trip-poi__icon {
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

.add-trip-poi > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.add-trip-poi strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.add-trip-poi p {
  overflow: hidden;
  margin-top: 2px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.add-trip-form :deep(.el-form-item__label) {
  padding-bottom: 5px;
  color: var(--ta-ink-secondary);
  font-size: 12px;
  font-weight: 500;
}

.add-trip-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.add-trip-control {
  width: 100%;
}

.add-trip-state,
.add-trip-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  padding: 30px 16px;
  text-align: center;
}

.add-trip-state p,
.add-trip-empty p {
  max-width: 390px;
  color: var(--ta-ink-muted);
  font-size: 11px;
  line-height: 1.7;
}

.add-trip-empty__icon {
  display: grid;
  width: 46px;
  height: 46px;
  margin-bottom: 5px;
  border-radius: 14px;
  color: var(--ta-primary);
  font-size: 22px;
  place-items: center;
  background: #eaf4ff;
}

.add-trip-empty strong {
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
}

.add-trip-empty .el-button {
  margin-top: 5px;
}

.add-trip-detail-loading {
  padding: 4px 0;
}

.add-trip-detail-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #f1e1d8;
  border-radius: 9px;
  color: #9a6a5c;
  font-size: 11px;
  background: #fff9f7;
}

.add-trip-day-hint {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.add-trip-day-hint__limit {
  color: #b47a1a;
}

@media (max-width: 600px) {
  .add-trip-form__grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
