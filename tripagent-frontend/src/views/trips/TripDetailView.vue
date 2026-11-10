<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Calendar,
  Delete,
  EditPen,
  Food,
  House,
  MapLocation,
  Refresh,
  User,
  Wallet,
  WarningFilled,
} from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import TripCover from '@/components/trips/TripCover.vue'
import TripItinerary from '@/components/trips/TripItinerary.vue'
import TripRouteDrawer from '@/components/trips/TripRouteDrawer.vue'
import { deleteTrip, getTripDetail, updateTrip } from '@/api/trips'
import type { TripDetail, TripDraft } from '@/types/trip'
import { ApiError } from '@/utils/request'
import { formatDateTime } from '@/utils/format'
import {
  countDraftItems,
  createEmptyTripDraft,
  createTripDraftFromDetail,
  snapshotTripDraft,
  validateTripDraft,
  buildUpdateTripPayload,
} from '@/utils/trip-draft'
import {
  deriveTripStatus,
  formatTripDateRange,
  getBudgetLevelLabel,
  getCrowdLabel,
  getSourceLabel,
  getValidationLabel,
} from '@/utils/trip'

const route = useRoute()
const router = useRouter()

const tripId = computed(() => String(route.params.tripId ?? ''))

const trip = ref<TripDetail | null>(null)
const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const deleting = ref(false)
const editing = ref(false)
const conflictMessage = ref('')

const draft = reactive<TripDraft>(createEmptyTripDraft())
const baseline = ref('')

const routeDrawerVisible = ref(false)
const routeInitialDay = ref(1)

const isDirty = computed(
  () =>
    editing.value &&
    baseline.value !== '' &&
    snapshotTripDraft(draft) !== baseline.value,
)

const status = computed(() =>
  trip.value
    ? deriveTripStatus(trip.value.startDate, trip.value.endDate)
    : { key: 'upcoming', label: '待出行' },
)

const itemCount = computed(() => countDraftItems(draft.days))

const validationLabel = computed(() =>
  getValidationLabel(trip.value?.plan.validationStatus),
)

const validationTone = computed(() => {
  if (trip.value?.plan.validationStatus === 'done') {
    return 'success'
  }
  if (trip.value?.plan.validationStatus === 'pending') {
    return 'pending'
  }
  return 'muted'
})

const warnings = computed(() => trip.value?.plan.warnings ?? [])

const diningSuggestions = computed(
  () => trip.value?.plan.diningSuggestions ?? [],
)

const assumptionsNotes = computed(
  () => trip.value?.plan.assumptions?.notes ?? [],
)

const hasExtras = computed(
  () =>
    Boolean(trip.value?.plan.budgetEstimate?.total) ||
    Boolean(trip.value?.plan.lodgingArea?.name) ||
    diningSuggestions.value.length > 0 ||
    assumptionsNotes.value.length > 0,
)

const sourceLabel = computed(() => getSourceLabel(trip.value?.source))

function resetDraftFromTrip(): void {
  if (!trip.value) {
    Object.assign(draft, createEmptyTripDraft())
    baseline.value = snapshotTripDraft(draft)
    return
  }

  const next = createTripDraftFromDetail(trip.value)
  draft.title = next.title
  draft.destination = next.destination
  draft.startDate = next.startDate
  draft.endDate = next.endDate
  draft.description = next.description
  draft.days = next.days
  baseline.value = snapshotTripDraft(draft)
}

async function loadTrip(): Promise<boolean> {
  if (!tripId.value) {
    loadError.value = '缺少行程 ID'
    loading.value = false
    return false
  }

  loading.value = true
  loadError.value = ''

  let succeeded = false
  try {
    const detail = await getTripDetail(tripId.value)
    trip.value = detail
    resetDraftFromTrip()
    editing.value = false
    conflictMessage.value = ''
    succeeded = true
  } catch (error) {
    loadError.value =
      error instanceof Error ? error.message : '行程详情加载失败'
    trip.value = null
  } finally {
    loading.value = false
  }
  return succeeded
}

onMounted(() => {
  void loadTrip()
})

function goBack(): void {
  void router.push({ name: 'trips' })
}

function startEdit(): void {
  if (!trip.value || saving.value) {
    return
  }
  resetDraftFromTrip()
  editing.value = true
  conflictMessage.value = ''
}

async function cancelEdit(): Promise<void> {
  if (saving.value) {
    return
  }

  if (conflictMessage.value) {
    await confirmReloadLatest()
    return
  }

  if (!isDirty.value) {
    resetDraftFromTrip()
    editing.value = false
    return
  }

  try {
    await ElMessageBox.confirm(
      '当前有未保存的修改，退出编辑后这些修改会丢失。',
      '放弃修改？',
      {
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
        type: 'warning',
      },
    )
    resetDraftFromTrip()
    editing.value = false
  } catch {
    // 用户继续编辑。
  }
}

async function saveTrip(): Promise<void> {
  if (!trip.value || saving.value || conflictMessage.value) {
    return
  }

  const validationMessage = validateTripDraft(draft)
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }

  saving.value = true
  try {
    const updated = await updateTrip(
      trip.value.id,
      buildUpdateTripPayload(trip.value, draft),
    )
    trip.value = updated
    resetDraftFromTrip()
    editing.value = false
    conflictMessage.value = ''
    ElMessage.success('行程已保存')
  } catch (error) {
    if (
      error instanceof ApiError &&
      (error.code === 40902 || error.status === 409)
    ) {
      await handleVersionConflict(error.message)
    } else {
      ElMessage.error(
        error instanceof Error && error.message
          ? error.message
          : '行程保存失败，请稍后重试',
      )
    }
  } finally {
    saving.value = false
  }
}

async function handleVersionConflict(message: string): Promise<void> {
  conflictMessage.value =
    '该行程已在其他窗口或页面被修改，当前编辑基于旧版本。为避免覆盖最新数据，保存已暂停。'

  try {
    await ElMessageBox.confirm(
      `${message || '行程数据已变化'}。加载最新行程后，当前未保存的修改将被丢弃。`,
      '行程版本冲突',
      {
        confirmButtonText: '加载最新行程',
        cancelButtonText: '留在此页',
        type: 'warning',
      },
    )
    await reloadLatest()
  } catch {
    // 用户选择留在此页，保存保持暂停。
  }
}

async function confirmReloadLatest(): Promise<void> {
  if (saving.value) {
    return
  }

  if (editing.value && isDirty.value) {
    try {
      await ElMessageBox.confirm(
        '加载最新行程会丢弃当前未保存的修改，确定继续吗？',
        '加载最新行程？',
        {
          confirmButtonText: '加载最新',
          cancelButtonText: '留在此页',
          type: 'warning',
        },
      )
    } catch {
      return
    }
  }

  await reloadLatest()
}

async function reloadLatest(): Promise<void> {
  editing.value = false
  conflictMessage.value = ''
  const succeeded = await loadTrip()
  if (succeeded) {
    ElMessage.info('已加载最新行程，请重新编辑')
  } else {
    ElMessage.error('加载最新行程失败，请重试')
  }
}

async function deleteCurrentTrip(): Promise<void> {
  if (!trip.value || deleting.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `删除行程「${trip.value.title}」后不可恢复，确定要删除吗？`,
      '删除行程',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }

  deleting.value = true
  try {
    await deleteTrip(trip.value.id)
    ElMessage.success('行程已删除')
    await router.replace({ name: 'trips' })
  } catch {
    // 删除失败信息由请求层统一提示。
  } finally {
    deleting.value = false
  }
}

function openRoute(dayIndex = 1): void {
  routeInitialDay.value = dayIndex
  routeDrawerVisible.value = true
}

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (editing.value && isDirty.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

window.addEventListener('beforeunload', handleBeforeUnload)

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(async () => {
  if (!editing.value || !isDirty.value) {
    return true
  }

  try {
    await ElMessageBox.confirm(
      '当前行程有未保存的修改，离开后这些修改会丢失。',
      '离开编辑？',
      {
        confirmButtonText: '离开',
        cancelButtonText: '继续编辑',
        type: 'warning',
      },
    )
    return true
  } catch {
    return false
  }
})
</script>

<template>
  <div class="page trip-detail-page">
    <div class="detail-back-row">
      <el-button text :icon="ArrowLeft" @click="goBack">
        返回我的行程
      </el-button>
    </div>

    <div v-if="loading" class="detail-loading surface-card">
      <el-skeleton animated :rows="5" />
    </div>

    <div v-else-if="loadError || !trip" class="detail-error surface-card">
      <EmptyState
        :icon="WarningFilled"
        title="行程加载失败"
        :description="loadError || '该行程不存在或已被删除。'"
      >
        <el-button round @click="goBack">返回我的行程</el-button>
        <el-button type="primary" round @click="loadTrip">重新加载</el-button>
      </EmptyState>
    </div>

    <template v-else>
      <section class="detail-hero surface-card">
        <TripCover :destination="trip.destination" size="large" />

        <div class="detail-hero__main">
          <div class="detail-hero__title-row">
            <h1>{{ trip.title }}</h1>
            <span
              class="trip-status"
              :class="`trip-status--${status.key}`"
            >
              {{ status.label }}
            </span>
            <span v-if="editing" class="stage-badge">编辑中</span>
          </div>

          <p v-if="trip.description" class="detail-hero__desc">
            {{ trip.description }}
          </p>

          <div class="detail-hero__meta">
            <span>
              <el-icon><Calendar /></el-icon>
              {{ formatTripDateRange(trip.startDate, trip.endDate) }}
            </span>
            <span>{{ trip.daysCount }} 天</span>
            <span>{{ itemCount }} 个安排</span>
            <span class="detail-hero__source">{{ sourceLabel }}</span>
            <span>更新于 {{ formatDateTime(trip.updateTime) }}</span>
          </div>
        </div>

        <div class="detail-hero__actions">
          <template v-if="!editing">
            <el-button :icon="MapLocation" @click="openRoute(1)">
              查看路线
            </el-button>
            <el-button type="primary" :icon="EditPen" @click="startEdit">
              编辑行程
            </el-button>
            <el-button
              type="danger"
              plain
              :icon="Delete"
              :loading="deleting"
              @click="deleteCurrentTrip"
            >
              删除行程
            </el-button>
          </template>

          <template v-else>
            <el-button :disabled="saving" @click="cancelEdit">
              取消编辑
            </el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!isDirty || Boolean(conflictMessage)"
              @click="saveTrip"
            >
              保存行程
            </el-button>
          </template>
        </div>
      </section>

      <div
        v-if="conflictMessage"
        class="detail-banner detail-banner--warning"
      >
        <el-icon><WarningFilled /></el-icon>
        <div>
          <strong>行程版本冲突</strong>
          <p>{{ conflictMessage }}</p>
        </div>
        <el-button
          size="small"
          :icon="Refresh"
          @click="confirmReloadLatest"
        >
          加载最新行程
        </el-button>
      </div>

      <div v-else-if="editing" class="detail-banner detail-banner--info">
        <el-icon><EditPen /></el-icon>
        <div>
          <strong>正在手动编辑</strong>
          <p>
            修改会统一保存，不会调用 Agent；保存后路线校验结果会被清空，需要重新校验。
          </p>
        </div>
        <span class="detail-banner__tag">
          {{ isDirty ? '有未保存修改' : '暂无修改' }}
        </span>
      </div>

      <div class="detail-layout">
        <section class="detail-itinerary surface-card">
          <header class="detail-section-head">
            <div>
              <h2>每日行程</h2>
              <p>
                {{ editing ? '可编辑景点字段、拖动调整顺序或移动到其他日期。' : '按日期查看每一天的景点与安排。' }}
              </p>
            </div>
          </header>

          <TripItinerary
            :days="draft.days"
            :editing="editing"
            :disabled="saving"
            :warnings="editing ? [] : warnings"
            @open-route="openRoute"
          />
        </section>

        <aside class="detail-aside">
          <section class="detail-info surface-card">
            <header class="detail-side-head">
              <h2>行程信息</h2>
            </header>

            <div class="info-row">
              <span>来源</span>
              <strong>{{ sourceLabel }}</strong>
            </div>
            <div class="info-row">
              <span>路线校验</span>
              <strong :class="`validation-text--${validationTone}`">
                {{ validationLabel }}
              </strong>
            </div>
            <div v-if="warnings.length" class="info-warning">
              <el-icon><WarningFilled /></el-icon>
              <span>有 {{ warnings.length }} 条路线提醒</span>
              <el-button text size="small" @click="openRoute(1)">
                查看路线
              </el-button>
            </div>
            <div class="info-row">
              <span>创建时间</span>
              <strong>{{ formatDateTime(trip.createTime) }}</strong>
            </div>
            <div class="info-row">
              <span>最后更新</span>
              <strong>{{ formatDateTime(trip.updateTime) }}</strong>
            </div>

            <div class="info-divider" />

            <button
              type="button"
              class="info-route-entry"
              @click="openRoute(1)"
            >
              <span class="info-route-entry__icon">
                <el-icon><MapLocation /></el-icon>
              </span>
              <span>
                <strong>查看每日路线</strong>
                <small>站点顺序 · 坐标 · 路线提醒</small>
              </span>
              <el-icon class="info-route-entry__arrow">
                <MapLocation />
              </el-icon>
            </button>
          </section>

          <section v-if="hasExtras" class="detail-extras surface-card">
            <header class="detail-side-head">
              <h2>旅行摘要</h2>
            </header>

            <div
              v-if="trip.plan.budgetEstimate?.total"
              class="extra-block"
            >
              <h3><el-icon><Wallet /></el-icon>预算范围</h3>
              <strong class="extra-value">
                {{ trip.plan.budgetEstimate.total }}
              </strong>
              <p v-if="trip.plan.budgetEstimate.breakdown">
                {{ trip.plan.budgetEstimate.breakdown }}
              </p>
            </div>

            <div
              v-if="
                trip.plan.assumptions?.crowd ||
                trip.plan.assumptions?.budgetLevel
              "
              class="extra-tags"
            >
              <span v-if="trip.plan.assumptions?.crowd">
                {{ getCrowdLabel(trip.plan.assumptions.crowd) }}
              </span>
              <span v-if="trip.plan.assumptions?.budgetLevel">
                {{ getBudgetLevelLabel(trip.plan.assumptions.budgetLevel) }}
              </span>
            </div>

            <div v-if="trip.plan.lodgingArea?.name" class="extra-block">
              <h3><el-icon><House /></el-icon>住宿区域</h3>
              <strong class="extra-value">
                {{ trip.plan.lodgingArea.name }}
              </strong>
              <p v-if="trip.plan.lodgingArea.reason">
                {{ trip.plan.lodgingArea.reason }}
              </p>
            </div>

            <div v-if="diningSuggestions.length" class="extra-block">
              <h3><el-icon><Food /></el-icon>美食推荐</h3>
              <ul class="dining-list">
                <li
                  v-for="(suggestion, index) in diningSuggestions"
                  :key="suggestion.area ?? index"
                >
                  <div>
                    <strong>{{ suggestion.area || '推荐区域' }}</strong>
                    <span v-if="suggestion.cuisine">
                      {{ suggestion.cuisine }}
                    </span>
                  </div>
                  <p v-if="suggestion.note">{{ suggestion.note }}</p>
                </li>
              </ul>
            </div>

            <div v-if="assumptionsNotes.length" class="extra-block">
              <h3><el-icon><User /></el-icon>规划假设</h3>
              <ul class="assumption-list">
                <li
                  v-for="(note, index) in assumptionsNotes"
                  :key="index"
                >
                  {{ note }}
                </li>
              </ul>
            </div>
          </section>

        </aside>
      </div>

      <div v-if="editing" class="detail-edit-bar">
        <span class="detail-edit-bar__status">
          <i :class="{ 'is-dirty': isDirty }" />
          {{ isDirty ? '有未保存的修改' : '暂无修改' }}
        </span>
        <div class="detail-edit-bar__actions">
          <el-button :disabled="saving" @click="cancelEdit">取消</el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!isDirty || Boolean(conflictMessage)"
            @click="saveTrip"
          >
            保存行程
          </el-button>
        </div>
      </div>

      <TripRouteDrawer
        v-model="routeDrawerVisible"
        :days="draft.days"
        :warnings="warnings"
        :validation-status="trip.plan.validationStatus"
        :initial-day-index="routeInitialDay"
      />
    </template>
  </div>
</template>

<style scoped>
.trip-detail-page {
  max-width: 1280px;
  margin: 0 auto;
  padding-bottom: 24px;
}

.detail-back-row {
  margin-top: -4px;
}

.detail-loading {
  padding: 32px;
}

.detail-error {
  overflow: hidden;
}

.detail-hero {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 22px;
  overflow: hidden;
  background:
    radial-gradient(circle at 92% 8%, rgba(117, 174, 255, 0.16), transparent 34%),
    rgba(255, 255, 255, 0.94);
}

.detail-hero__main {
  flex: 1;
  min-width: 0;
}

.detail-hero__title-row {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

.detail-hero__title-row h1 {
  color: var(--ta-ink-strong);
  font-size: 25px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.025em;
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

.detail-hero__desc {
  display: -webkit-box;
  overflow: hidden;
  max-width: 680px;
  margin-top: 7px;
  color: var(--ta-ink-secondary);
  font-size: 12px;
  line-height: 1.75;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.detail-hero__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px 12px;
  margin-top: 12px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.detail-hero__meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.detail-hero__source {
  padding: 1px 7px;
  border-radius: 4px;
  color: #6983a5;
  background: #f2f7fd;
}

.detail-hero__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  max-width: 390px;
  justify-content: flex-end;
}

.detail-banner {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 14px;
  border: 1px solid #dcebfb;
  border-radius: 12px;
  color: var(--ta-ink-secondary);
  font-size: 11px;
  background: #f5faff;
}

.detail-banner > .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--ta-primary);
}

.detail-banner div {
  flex: 1;
  min-width: 0;
}

.detail-banner strong {
  color: var(--ta-ink);
  font-size: 11.5px;
  font-weight: 600;
}

.detail-banner p {
  margin-top: 2px;
  line-height: 1.7;
}

.detail-banner--warning {
  border-color: #f4dfbc;
  color: #8a672a;
  background: #fffaf1;
}

.detail-banner--warning > .el-icon {
  color: #c58a27;
}

.detail-banner__tag {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 999px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  background: #eef5fd;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 318px;
  align-items: start;
  gap: 20px;
}

.detail-itinerary {
  padding: 20px;
}

.detail-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.detail-section-head h2,
.detail-side-head h2 {
  color: var(--ta-ink);
  font-size: 16px;
  font-weight: 600;
}

.detail-section-head p {
  margin-top: 3px;
  color: var(--ta-ink-muted);
  font-size: 11px;
  line-height: 1.7;
}

.detail-aside {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-info,
.detail-extras {
  padding: 18px;
}

.detail-side-head {
  margin-bottom: 12px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 7px 0;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.info-row strong {
  overflow: hidden;
  color: var(--ta-ink-secondary);
  font-size: 10.5px;
  font-weight: 500;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.validation-text--success {
  color: #1f8c62 !important;
}

.validation-text--pending {
  color: #a97618 !important;
}

.validation-text--muted {
  color: var(--ta-ink-muted) !important;
}

.info-warning {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
  margin: 4px 0;
  padding: 8px 9px;
  border: 1px solid #f4dfbc;
  border-radius: 8px;
  color: #8a672a;
  font-size: 10px;
  background: #fffaf1;
}

.info-warning .el-button {
  margin-left: auto;
}

.info-divider {
  height: 1px;
  margin: 10px 0;
  background: #eef5fc;
}

.info-route-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px;
  border: 1px solid #e8f1fb;
  border-radius: 11px;
  text-align: left;
  background: #f9fcff;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease;
}

.info-route-entry:hover {
  border-color: #c7dcf7;
  background: #f3f9ff;
}

.info-route-entry__icon {
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

.info-route-entry > span:nth-child(2) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.info-route-entry strong {
  color: var(--ta-ink);
  font-size: 10.5px;
  font-weight: 600;
}

.info-route-entry small {
  color: var(--ta-ink-muted);
  font-size: 9px;
}

.info-route-entry__arrow {
  color: #a5b9d0;
  font-size: 12px;
}

.extra-block + .extra-block {
  margin-top: 15px;
  padding-top: 14px;
  border-top: 1px dashed #eef4fb;
}

.extra-block h3 {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  font-weight: 500;
}

.extra-value {
  display: block;
  margin-top: 5px;
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
}

.extra-block p {
  margin-top: 4px;
  color: var(--ta-ink-muted);
  font-size: 10px;
  line-height: 1.65;
}

.extra-tags {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.extra-tags span {
  padding: 2px 7px;
  border-radius: 999px;
  color: #5e7795;
  font-size: 9px;
  background: #f1f6fc;
}

.dining-list,
.assumption-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.dining-list li {
  padding: 8px 9px;
  border-radius: 8px;
  background: #fbfdff;
}

.dining-list li > div {
  display: flex;
  align-items: baseline;
  gap: 5px;
  flex-wrap: wrap;
}

.dining-list strong {
  color: var(--ta-ink);
  font-size: 10.5px;
  font-weight: 500;
}

.dining-list span {
  color: var(--ta-ink-muted);
  font-size: 9px;
}

.dining-list p {
  margin-top: 2px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.6;
}

.assumption-list {
  gap: 3px;
  padding-left: 15px;
  list-style: disc;
  color: var(--ta-ink-muted);
  font-size: 10px;
  line-height: 1.65;
}

.detail-edit-bar {
  position: sticky;
  bottom: 18px;
  z-index: 15;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 11px 15px;
  border: 1px solid rgba(214, 232, 250, 0.96);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 34px rgba(39, 93, 170, 0.15);
  backdrop-filter: blur(16px);
}

.detail-edit-bar__status {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ta-ink-secondary);
  font-size: 11px;
}

.detail-edit-bar__status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #b7c9dc;
}

.detail-edit-bar__status i.is-dirty {
  background: var(--ta-warning);
  box-shadow: 0 0 0 3px rgba(233, 162, 59, 0.14);
}

.detail-edit-bar__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

@media (max-width: 1100px) {
  .detail-layout {
    grid-template-columns: minmax(0, 1fr) 280px;
    gap: 16px;
  }

  .detail-hero__actions {
    max-width: 220px;
  }
}

@media (max-width: 960px) {
  .detail-hero {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .detail-hero__actions {
    max-width: none;
    width: 100%;
    justify-content: flex-start;
  }

  .detail-layout {
    grid-template-columns: 1fr;
  }

  .detail-aside {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
}

@media (max-width: 760px) {
  .detail-hero {
    flex-direction: column;
    gap: 14px;
    padding: 16px;
  }

  .detail-hero__main {
    width: 100%;
  }

  .detail-hero__title-row h1 {
    font-size: 21px;
  }

  .detail-hero__actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 7px;
    width: 100%;
  }

  .detail-hero__actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .detail-banner {
    flex-wrap: wrap;
  }

  .detail-itinerary {
    padding: 14px;
  }

  .detail-aside {
    display: flex;
  }

  .detail-info,
  .detail-extras {
    padding: 16px;
  }

  .detail-edit-bar {
    bottom: 80px;
    align-items: stretch;
    flex-direction: column;
    gap: 9px;
    padding: 10px 12px;
  }

  .detail-edit-bar__actions {
    justify-content: flex-end;
  }
}
</style>
