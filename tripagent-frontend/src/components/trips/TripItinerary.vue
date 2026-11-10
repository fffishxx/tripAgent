<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  ArrowUp,
  Calendar,
  Delete,
  EditPen,
  Location,
  Notebook,
  Plus,
  Position,
  Rank,
  WarningFilled,
} from '@element-plus/icons-vue'
import TripItemDialog from '@/components/trips/TripItemDialog.vue'
import { formatDuration } from '@/utils/format'
import {
  getDayWarnings,
  getItemTypeLabel,
  getWarningText,
  formatDayDate,
} from '@/utils/trip'
import type {
  TripDraftDay,
  TripDraftItem,
  TripWarning,
} from '@/types/trip'

const props = withDefaults(
  defineProps<{
    days: TripDraftDay[]
    editing?: boolean
    disabled?: boolean
    warnings?: TripWarning[] | null
    showRouteButton?: boolean
  }>(),
  {
    editing: false,
    disabled: false,
    warnings: null,
    showRouteButton: true,
  },
)

const emit = defineEmits<{
  openRoute: [dayIndex: number]
}>()

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogItem = ref<TripDraftItem | null>(null)
const dialogDayIndex = ref(1)

interface DragState {
  dayIndex: number | null
  itemId: string | null
  overDayIndex: number | null
  overItemId: string | null
}

const dragged = reactive<DragState>({
  dayIndex: null,
  itemId: null,
  overDayIndex: null,
  overItemId: null,
})

const isDragging = computed(() => dragged.itemId !== null)

function totalItems(): number {
  return props.days.reduce((total, day) => total + day.items.length, 0)
}

function warningsForDay(dayIndex: number): TripWarning[] {
  return getDayWarnings(props.warnings, dayIndex)
}

function openAdd(dayIndex: number): void {
  if (!props.editing || props.disabled) {
    return
  }
  dialogMode.value = 'create'
  dialogItem.value = null
  dialogDayIndex.value = dayIndex
  dialogVisible.value = true
}

function openEdit(dayIndex: number, item: TripDraftItem): void {
  if (!props.editing || props.disabled) {
    return
  }
  dialogMode.value = 'edit'
  dialogItem.value = item
  dialogDayIndex.value = dayIndex
  dialogVisible.value = true
}

function handleDialogSubmit(item: TripDraftItem, targetDayIndex: number): void {
  const targetDay = props.days.find((day) => day.dayIndex === targetDayIndex)
  if (!targetDay) {
    return
  }

  if (dialogMode.value === 'create') {
    if (targetDay.items.length >= 20) {
      ElMessage.warning(`第${targetDay.dayIndex}天的安排已达上限（20 个）`)
      return
    }
    if (
      targetDay.items.some(
        (candidate) => candidate.localId === item.localId,
      )
    ) {
      return
    }
    targetDay.items.push(item)
    return
  }

  const sourceDay = props.days.find((day) =>
    day.items.some((candidate) => candidate.localId === item.localId),
  )
  if (!sourceDay) {
    targetDay.items.push(item)
    return
  }

  const sourceIndex = sourceDay.items.findIndex(
    (candidate) => candidate.localId === item.localId,
  )
  if (sourceDay === targetDay) {
    sourceDay.items.splice(sourceIndex, 1, item)
  } else {
    if (targetDay.items.length >= 20) {
      ElMessage.warning(`第${targetDay.dayIndex}天的安排已达上限（20 个）`)
      return
    }
    sourceDay.items.splice(sourceIndex, 1)
    targetDay.items.push(item)
  }
}

function removeItem(dayIndex: number, item: TripDraftItem): void {
  if (!props.editing || props.disabled) {
    return
  }
  if (totalItems() <= 1) {
    ElMessage.warning('行程至少需要保留一个景点或安排')
    return
  }

  const day = props.days.find((candidate) => candidate.dayIndex === dayIndex)
  if (!day) {
    return
  }
  const index = day.items.findIndex((candidate) => candidate.localId === item.localId)
  if (index !== -1) {
    day.items.splice(index, 1)
  }
}

function moveWithinDay(
  dayIndex: number,
  itemIndex: number,
  direction: -1 | 1,
): void {
  if (!props.editing || props.disabled) {
    return
  }
  const day = props.days.find((candidate) => candidate.dayIndex === dayIndex)
  if (!day) {
    return
  }
  const targetIndex = itemIndex + direction
  if (targetIndex < 0 || targetIndex >= day.items.length) {
    return
  }
  const [item] = day.items.splice(itemIndex, 1)
  day.items.splice(targetIndex, 0, item)
}

function onDragStart(
  dayIndex: number,
  item: TripDraftItem,
  event: DragEvent,
): void {
  if (!props.editing || props.disabled) {
    return
  }
  dragged.dayIndex = dayIndex
  dragged.itemId = item.localId
  dragged.overDayIndex = dayIndex
  dragged.overItemId = null

  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', item.localId)
  }
}

function onDragOverItem(
  dayIndex: number,
  item: TripDraftItem,
  event: DragEvent,
): void {
  if (!isDragging.value) {
    return
  }
  dragged.overDayIndex = dayIndex
  dragged.overItemId = item.localId
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onDragOverDay(dayIndex: number, event: DragEvent): void {
  if (!isDragging.value) {
    return
  }
  dragged.overDayIndex = dayIndex
  dragged.overItemId = null
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function resetDrag(): void {
  dragged.dayIndex = null
  dragged.itemId = null
  dragged.overDayIndex = null
  dragged.overItemId = null
}

function moveDraggedTo(targetDayIndex: number, targetItemId: string | null): void {
  if (!isDragging.value || dragged.dayIndex === null || !dragged.itemId) {
    resetDrag()
    return
  }
  if (targetItemId === dragged.itemId) {
    resetDrag()
    return
  }

  const sourceDay = props.days.find((day) => day.dayIndex === dragged.dayIndex)
  const targetDay = props.days.find((day) => day.dayIndex === targetDayIndex)
  const sourceId = dragged.itemId

  if (!sourceDay || !targetDay) {
    resetDrag()
    return
  }

  const sourceIndex = sourceDay.items.findIndex((item) => item.localId === sourceId)
  if (sourceIndex === -1) {
    resetDrag()
    return
  }

  if (sourceDay !== targetDay && targetDay.items.length >= 20) {
    ElMessage.warning(`第${targetDay.dayIndex}天的安排已达上限（20 个）`)
    resetDrag()
    return
  }

  const [item] = sourceDay.items.splice(sourceIndex, 1)
  if (targetItemId) {
    const targetIndex = targetDay.items.findIndex(
      (candidate) => candidate.localId === targetItemId,
    )
    if (targetIndex === -1) {
      targetDay.items.push(item)
    } else {
      targetDay.items.splice(targetIndex, 0, item)
    }
  } else {
    targetDay.items.push(item)
  }

  resetDrag()
}

function onDropItem(dayIndex: number, item: TripDraftItem): void {
  moveDraggedTo(dayIndex, item.localId)
}

function onDropDay(dayIndex: number): void {
  moveDraggedTo(dayIndex, null)
}

function isDropBefore(dayIndex: number, item: TripDraftItem): boolean {
  return (
    isDragging.value &&
    dragged.overDayIndex === dayIndex &&
    dragged.overItemId === item.localId &&
    dragged.itemId !== item.localId
  )
}

function isDayDragOver(dayIndex: number): boolean {
  return isDragging.value && dragged.overDayIndex === dayIndex
}
</script>

<template>
  <div class="trip-itinerary">
    <div v-if="days.length === 0" class="trip-itinerary-empty">
      <el-icon><Calendar /></el-icon>
      <strong>暂无每日行程</strong>
      <p>添加景点或调整日期后，每日安排会显示在这里。</p>
    </div>

    <article
      v-for="day in days"
      :key="day.dayIndex"
      class="trip-day-card"
      :class="{ 'trip-day-card--editing': editing }"
    >
      <header class="trip-day-card__head">
        <span class="trip-day-card__index">第{{ day.dayIndex }}天</span>

        <div class="trip-day-card__title">
          <strong>{{ day.theme || '当日安排' }}</strong>
          <small>
            {{ formatDayDate(day.dayDate) }} · {{ day.items.length }} 个安排
          </small>
        </div>

        <div class="trip-day-card__actions">
          <el-button
            v-if="!editing && showRouteButton"
            text
            size="small"
            :icon="Position"
            @click="emit('openRoute', day.dayIndex)"
          >
            查看路线
          </el-button>
          <el-button
            v-if="editing"
            type="primary"
            plain
            size="small"
            :icon="Plus"
            :disabled="disabled || day.items.length >= 20"
            @click="openAdd(day.dayIndex)"
          >
            添加安排
          </el-button>
        </div>
      </header>

      <div
        class="trip-day-card__body"
        :class="{
          'trip-day-card__body--drag-over':
            editing && isDayDragOver(day.dayIndex),
        }"
        @dragover.prevent="editing && onDragOverDay(day.dayIndex, $event)"
        @drop.prevent="editing && onDropDay(day.dayIndex)"
      >
        <div v-if="day.items.length === 0" class="trip-day-empty">
          <span>{{ editing ? '当天还没有安排' : '当天为自由活动' }}</span>
          <el-button
            v-if="editing"
            text
            type="primary"
            size="small"
            @click="openAdd(day.dayIndex)"
          >
            添加第一个安排
          </el-button>
        </div>

        <div v-else class="trip-items">
          <div
            v-for="(item, index) in day.items"
            :key="item.localId"
            class="trip-item"
            :class="{
              'trip-item--editing': editing,
              'trip-item--dragged': editing && dragged.itemId === item.localId,
              'trip-item--drop-before': isDropBefore(day.dayIndex, item),
            }"
            @dragover.prevent.stop="editing && onDragOverItem(day.dayIndex, item, $event)"
            @drop.prevent.stop="editing && onDropItem(day.dayIndex, item)"
          >
            <template v-if="editing">
              <span
                class="trip-item__drag"
                draggable="true"
                title="拖拽调整顺序或移动到其他日期"
                @dragstart="onDragStart(day.dayIndex, item, $event)"
                @dragend="resetDrag"
              >
                <el-icon><Rank /></el-icon>
              </span>

              <span class="trip-item__index">{{ index + 1 }}</span>

              <div class="trip-item__edit-main">
                <div class="trip-item__name-row">
                  <span class="trip-item__time">
                    {{ item.timeSlot || '未指定' }}
                  </span>
                  <strong>{{ item.name }}</strong>
                  <small v-if="item.durationMin">
                    {{ formatDuration(item.durationMin) }}
                  </small>
                </div>
                <div class="trip-item__edit-meta">
                  <span>{{ getItemTypeLabel(item.itemType) }}</span>
                  <span v-if="item.address">· {{ item.address }}</span>
                </div>
              </div>

              <div class="trip-item__actions">
                <el-tooltip content="上移" placement="top">
                  <el-button
                    text
                    circle
                    size="small"
                    :icon="ArrowUp"
                    :disabled="disabled || index === 0"
                    aria-label="上移"
                    @click="moveWithinDay(day.dayIndex, index, -1)"
                  />
                </el-tooltip>
                <el-tooltip content="下移" placement="top">
                  <el-button
                    text
                    circle
                    size="small"
                    :icon="ArrowDown"
                    :disabled="disabled || index === day.items.length - 1"
                    aria-label="下移"
                    @click="moveWithinDay(day.dayIndex, index, 1)"
                  />
                </el-tooltip>
                <el-tooltip content="编辑景点" placement="top">
                  <el-button
                    text
                    circle
                    size="small"
                    :icon="EditPen"
                    :disabled="disabled"
                    aria-label="编辑景点"
                    @click="openEdit(day.dayIndex, item)"
                  />
                </el-tooltip>

                <el-popconfirm
                  width="240"
                  :title="`从行程中移除「${item.name}」？`"
                  confirm-button-text="移除"
                  cancel-button-text="取消"
                  @confirm="removeItem(day.dayIndex, item)"
                >
                  <template #reference>
                    <el-button
                      text
                      circle
                      size="small"
                      type="danger"
                      :icon="Delete"
                      :disabled="disabled"
                      aria-label="移除安排"
                    />
                  </template>
                </el-popconfirm>
              </div>
            </template>

            <template v-else>
              <span class="trip-item__marker">{{ index + 1 }}</span>
              <div class="trip-item__main">
                <div class="trip-item__name-row">
                  <span class="trip-item__time">
                    {{ item.timeSlot || '未指定' }}
                  </span>
                  <strong>{{ item.name }}</strong>
                  <span class="trip-item__type">
                    {{ getItemTypeLabel(item.itemType) }}
                  </span>
                  <small v-if="item.durationMin">
                    {{ formatDuration(item.durationMin) }}
                  </small>
                </div>

                <p v-if="item.address" class="trip-item__address">
                  <el-icon><Location /></el-icon>
                  {{ item.address }}
                </p>
                <p v-if="item.reason" class="trip-item__reason">
                  {{ item.reason }}
                </p>
                <p v-if="item.notes" class="trip-item__notes">
                  <el-icon><Notebook /></el-icon>
                  {{ item.notes }}
                </p>
              </div>
            </template>
          </div>
        </div>

        <div
          v-if="editing && !isDragging && day.items.length >= 20"
          class="trip-day-limit"
        >
          单日安排已达上限（20 个）
        </div>
      </div>

      <div v-if="!editing && warningsForDay(day.dayIndex).length" class="trip-day-warnings">
        <div class="trip-day-warnings__head">
          <el-icon><WarningFilled /></el-icon>
          路线提醒
        </div>
        <ul>
          <li
            v-for="(warning, index) in warningsForDay(day.dayIndex)"
            :key="index"
          >
            {{ getWarningText(warning) }}
          </li>
        </ul>
      </div>
    </article>

    <TripItemDialog
      v-model="dialogVisible"
      :mode="dialogMode"
      :item="dialogItem"
      :days="days"
      :default-day-index="dialogDayIndex"
      @submit="handleDialogSubmit"
    />
  </div>
</template>

<style scoped>
.trip-itinerary {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.trip-itinerary-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 5px;
  padding: 34px 16px;
  border: 1px dashed #dbe8f7;
  border-radius: 16px;
  color: var(--ta-ink-muted);
  text-align: center;
  background: rgba(249, 252, 255, 0.8);
}

.trip-itinerary-empty .el-icon {
  color: #9cb4d2;
  font-size: 22px;
}

.trip-itinerary-empty strong {
  color: var(--ta-ink-secondary);
  font-size: 12px;
  font-weight: 500;
}

.trip-itinerary-empty p {
  font-size: 10px;
}

.trip-day-card {
  overflow: hidden;
  border: 1px solid #e4eef9;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.trip-day-card--editing {
  border-color: #d9e8fb;
  box-shadow: 0 10px 26px rgba(47, 100, 170, 0.06);
}

.trip-day-card__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 18px;
  border-bottom: 1px solid #eef5fc;
  background: linear-gradient(135deg, rgba(246, 250, 255, 0.94), rgba(255, 255, 255, 0.92));
}

.trip-day-card__index {
  flex: 0 0 auto;
  padding: 4px 9px;
  border-radius: 7px;
  color: var(--ta-primary);
  font-size: 11px;
  font-weight: 600;
  background: #eaf4ff;
}

.trip-day-card__title {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.trip-day-card__title strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-day-card__title small {
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.trip-day-card__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}

.trip-day-card__body {
  padding: 4px 18px 16px;
  transition: background 0.18s ease;
}

.trip-day-card__body--drag-over {
  background: #f5faff;
}

.trip-items {
  display: flex;
  flex-direction: column;
}

.trip-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 13px 2px;
  border-bottom: 1px dashed #edf4fb;
  transition:
    opacity 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.trip-item:last-child {
  border-bottom: 0;
}

.trip-item--drop-before::before {
  position: absolute;
  top: -1px;
  right: 0;
  left: 0;
  height: 2px;
  border-radius: 2px;
  content: '';
  background: var(--ta-primary);
}

.trip-item--dragged {
  opacity: 0.4;
}

.trip-item__marker {
  display: grid;
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  margin-top: 1px;
  border: 1px solid #cfe2f8;
  border-radius: 50%;
  color: var(--ta-primary);
  font-size: 10px;
  font-weight: 600;
  place-items: center;
  background: #f3f9ff;
}

.trip-item__main {
  flex: 1;
  min-width: 0;
}

.trip-item__name-row {
  display: flex;
  align-items: baseline;
  gap: 7px;
  flex-wrap: wrap;
}

.trip-item__name-row strong {
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
}

.trip-item__name-row small {
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.trip-item__time {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 5px;
  color: var(--ta-primary);
  font-size: 9.5px;
  font-weight: 600;
  background: #eaf4ff;
}

.trip-item__type {
  padding: 1px 6px;
  border-radius: 4px;
  color: #6a83a2;
  font-size: 9px;
  background: #f1f6fc;
}

.trip-item__address,
.trip-item__notes {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.6;
}

.trip-item__reason {
  margin-top: 4px;
  color: var(--ta-ink-secondary);
  font-size: 11px;
  line-height: 1.7;
}

.trip-item__notes {
  color: #8c7a5a;
}

.trip-item__drag {
  display: grid;
  flex: 0 0 auto;
  width: 20px;
  height: 28px;
  color: #a7b9cf;
  font-size: 16px;
  place-items: center;
  cursor: grab;
  user-select: none;
}

.trip-item__drag:active {
  cursor: grabbing;
}

.trip-item--editing {
  align-items: center;
  padding: 9px 2px;
}

.trip-item__index {
  display: grid;
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  color: #8398b2;
  font-size: 9px;
  place-items: center;
  background: #f1f6fc;
}

.trip-item__edit-main {
  flex: 1;
  min-width: 0;
}

.trip-item__edit-meta {
  display: flex;
  gap: 4px;
  margin-top: 2px;
  overflow: hidden;
  color: var(--ta-ink-muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-item__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0;
  opacity: 0.82;
  transition: opacity 0.18s ease;
}

.trip-item--editing:hover .trip-item__actions {
  opacity: 1;
}

.trip-item__actions :deep(.el-button) {
  width: 26px;
  height: 26px;
  font-size: 13px;
}

.trip-day-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  padding: 24px 12px;
  color: var(--ta-ink-muted);
  font-size: 11px;
  text-align: center;
}

.trip-day-limit {
  padding-top: 8px;
  color: #b08a50;
  font-size: 10px;
}

.trip-day-warnings {
  margin: 0 18px 16px;
  padding: 10px 12px;
  border: 1px solid #f6e2c1;
  border-radius: 10px;
  color: #8b6a31;
  background: #fffaf1;
}

.trip-day-warnings__head {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
}

.trip-day-warnings ul {
  margin: 4px 0 0;
  padding-left: 18px;
  font-size: 10.5px;
  line-height: 1.7;
}

@media (max-width: 760px) {
  .trip-day-card__head {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 13px 14px;
  }

  .trip-day-card__title {
    flex-basis: calc(100% - 56px);
  }

  .trip-day-card__actions {
    width: 100%;
    justify-content: flex-end;
  }

  .trip-day-card__body {
    padding: 2px 14px 12px;
  }

  .trip-item__actions {
    flex-wrap: wrap;
    justify-content: flex-end;
    max-width: 92px;
  }

  .trip-item__actions :deep(.el-button) {
    width: 24px;
    height: 24px;
  }

  .trip-item__reason {
    font-size: 10.5px;
  }

  .trip-day-warnings {
    margin: 0 14px 12px;
  }
}
</style>
