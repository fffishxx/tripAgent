<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  reactive,
  ref,
} from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import {
  ArrowLeft,
  Calendar,
  Plus,
  WarningFilled,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import TripItinerary from '@/components/trips/TripItinerary.vue'
import { createTrip } from '@/api/trips'
import type { TripDraft } from '@/types/trip'
import {
  createEmptyTripDraft,
  buildDraftDaysForRange,
  countDraftItems,
  validateTripDraft,
  buildCreateTripPayload,
} from '@/utils/trip-draft'
import { getTodayIsoDate } from '@/utils/trip'

const router = useRouter()

const formRef = ref<FormInstance>()
const saving = ref(false)
const leavingAfterSave = ref(false)
let disposed = false

const today = getTodayIsoDate()
const initialDays = buildDraftDaysForRange(today, today).days

const draft = reactive<TripDraft>({
  ...createEmptyTripDraft(),
  startDate: today,
  endDate: today,
  days: initialDays,
})

const rules: FormRules<TripDraft> = {
  destination: [
    { required: true, message: '请填写目的地', trigger: 'blur' },
    { max: 64, message: '目的地不能超过 64 个字符', trigger: 'blur' },
  ],
  title: [{ max: 100, message: '行程名称不能超过 100 个字符', trigger: 'blur' }],
  description: [
    { max: 1000, message: '行程描述不能超过 1000 个字符', trigger: 'blur' },
  ],
}

const totalItems = computed(() => countDraftItems(draft.days))

const dateRange = computed<string[] | null>({
  get() {
    return draft.startDate && draft.endDate
      ? [draft.startDate, draft.endDate]
      : null
  },
  set(value) {
    if (!value || value.length !== 2 || !value[0] || !value[1]) {
      draft.startDate = ''
      draft.endDate = ''
      draft.days = []
      return
    }

    const [startDate, endDate] = value
    const result = buildDraftDaysForRange(startDate, endDate, draft.days)
    draft.startDate = startDate
    draft.endDate = endDate
    draft.days = result.days

    if (result.removedItemCount > 0) {
      ElMessage.warning(
        `日期范围调整后，移除了 ${result.removedItemCount} 个超出范围的安排`,
      )
    }
  },
})

const hasContent = computed(
  () =>
    Boolean(
      draft.title.trim() ||
        draft.destination.trim() ||
        draft.description.trim() ||
        draft.startDate !== today ||
        draft.endDate !== today ||
        totalItems.value > 0,
    ),
)

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (hasContent.value && !leavingAfterSave.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

window.addEventListener('beforeunload', handleBeforeUnload)

onBeforeUnmount(() => {
  disposed = true
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(async () => {
  if (leavingAfterSave.value) {
    return true
  }
  if (saving.value) {
    ElMessage.warning('行程正在保存，请稍候')
    return false
  }
  if (!hasContent.value) {
    return true
  }

  try {
    await ElMessageBox.confirm(
      '当前创建的行程还没有保存，离开后填写的内容会丢失。',
      '离开创建页面？',
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

function goBack(): void {
  void router.push({ name: 'trips' })
}

async function handleCreate(): Promise<void> {
  if (saving.value) {
    return
  }

  let formValid = false
  try {
    formValid = (await formRef.value?.validate()) ?? false
  } catch {
    formValid = false
  }
  if (!formValid) {
    return
  }

  const validationMessage = validateTripDraft(draft)
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }

  saving.value = true
  try {
    const created = await createTrip(buildCreateTripPayload(draft))
    if (disposed) {
      return
    }
    leavingAfterSave.value = true
    ElMessage.success('行程创建成功')
    await router.replace({
      name: 'trip-detail',
      params: {
        tripId: created.id,
      },
    })
  } catch {
    // 创建失败信息由请求层统一提示。
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page create-trip-page">
    <PageHeader
      title="创建行程"
      description="手动填写日期、目的地与每日安排；手动创建不会调用 Agent。"
      :icon="Calendar"
    >
      <template #actions>
        <el-button text :icon="ArrowLeft" @click="goBack">
          返回我的行程
        </el-button>
      </template>
    </PageHeader>

    <section class="create-basic-card surface-card">
      <div class="create-section-head">
        <div>
          <h2>基础信息</h2>
          <p>选择日期后会自动生成对应天数，可逐天添加安排。</p>
        </div>
      </div>

      <el-form
        ref="formRef"
        :model="draft"
        :rules="rules"
        label-position="top"
        class="create-form"
      >
        <div class="create-form__grid">
          <el-form-item label="行程名称（可选）" prop="title">
            <el-input
              v-model="draft.title"
              maxlength="100"
              placeholder="例如：杭州三日游；不填将自动生成"
            />
          </el-form-item>

          <el-form-item label="目的地" prop="destination">
            <el-input
              v-model="draft.destination"
              maxlength="64"
              placeholder="例如：杭州"
            />
          </el-form-item>
        </div>

        <el-form-item label="行程日期" required>
          <div class="create-date-field">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :editable="false"
              class="create-date-picker"
            />
            <span v-if="draft.days.length" class="create-date-count">
              共 {{ draft.days.length }} 天 · {{ totalItems }} 个安排
            </span>
          </div>
        </el-form-item>

        <el-form-item label="行程描述（可选）" prop="description">
          <el-input
            v-model="draft.description"
            type="textarea"
            :rows="2"
            maxlength="1000"
            show-word-limit
            resize="none"
            placeholder="简单描述这次旅行的主题或注意事项"
          />
        </el-form-item>
      </el-form>

      <div class="create-tip">
        <el-icon><WarningFilled /></el-icon>
        手动创建/修改不会调用 Agent。保存后路线校验结果会显示为“未校验”，后续阶段可再接入地图与校验能力。
      </div>
    </section>

    <section class="create-itinerary-card surface-card">
      <div class="create-section-head">
        <div>
          <h2>每日安排</h2>
          <p>添加景点或活动，支持编辑字段、调整顺序和跨天移动。</p>
        </div>
        <span v-if="draft.days.length" class="create-day-count">
          {{ draft.days.length }} 天
        </span>
      </div>

      <div v-if="draft.days.length === 0" class="create-empty-days">
        <span class="create-empty-days__icon">
          <el-icon><Calendar /></el-icon>
        </span>
        <strong>先选择行程日期</strong>
        <p>日期区间确定后，这里会出现每一天的安排编辑区。</p>
      </div>

      <TripItinerary
        v-else
        :days="draft.days"
        editing
        :disabled="saving"
        :show-route-button="false"
      />
    </section>

    <div class="create-actions">
      <div class="create-actions__summary">
        <span class="create-actions__dot" />
        新行程尚未保存
        <small>共 {{ draft.days.length }} 天 · {{ totalItems }} 个安排</small>
      </div>
      <div class="create-actions__buttons">
        <el-button :disabled="saving" @click="goBack">取消</el-button>
        <el-button
          type="primary"
          :icon="Plus"
          :loading="saving"
          @click="handleCreate"
        >
          保存行程
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.create-trip-page {
  max-width: 1180px;
  margin: 0 auto;
  padding-bottom: 28px;
}

.create-basic-card,
.create-itinerary-card {
  padding: 22px 24px;
}

.create-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.create-section-head h2 {
  color: var(--ta-ink);
  font-size: 16px;
  font-weight: 600;
}

.create-section-head p {
  margin-top: 3px;
  color: var(--ta-ink-muted);
  font-size: 11.5px;
  line-height: 1.7;
}

.create-day-count {
  flex: 0 0 auto;
  padding: 3px 9px;
  border-radius: 999px;
  color: var(--ta-primary);
  font-size: 10px;
  background: #eaf4ff;
}

.create-form :deep(.el-form-item__label) {
  padding-bottom: 5px;
  color: var(--ta-ink-secondary);
  font-size: 12px;
  font-weight: 500;
}

.create-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.create-date-field {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}

.create-date-picker {
  flex: 1 1 320px;
  max-width: 420px;
}

.create-date-count {
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.create-tip {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 2px;
  padding: 10px 12px;
  border: 1px dashed #eddfc5;
  border-radius: 9px;
  color: #8a6b32;
  font-size: 10.5px;
  line-height: 1.7;
  background: #fffbf3;
}

.create-empty-days {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 5px;
  padding: 46px 20px;
  border: 1px dashed #dce9f8;
  border-radius: 14px;
  text-align: center;
  background: #fbfdff;
}

.create-empty-days__icon {
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 4px;
  border-radius: 15px;
  color: var(--ta-primary);
  font-size: 22px;
  place-items: center;
  background: #eaf4ff;
}

.create-empty-days strong {
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
}

.create-empty-days p {
  color: var(--ta-ink-muted);
  font-size: 11px;
}

.create-actions {
  position: sticky;
  bottom: 18px;
  z-index: 15;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border: 1px solid rgba(214, 232, 250, 0.94);
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 14px 34px rgba(39, 93, 170, 0.14);
  backdrop-filter: blur(16px);
}

.create-actions__summary {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--ta-ink-secondary);
  font-size: 11.5px;
}

.create-actions__summary small {
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.create-actions__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ta-warning);
  box-shadow: 0 0 0 3px rgba(233, 162, 59, 0.14);
}

.create-actions__buttons {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

@media (max-width: 760px) {
  .create-basic-card,
  .create-itinerary-card {
    padding: 18px 16px;
  }

  .create-form__grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .create-date-picker {
    flex-basis: 100%;
    max-width: none;
  }

  .create-actions {
    bottom: 80px;
    align-items: stretch;
    flex-direction: column;
    gap: 9px;
    padding: 11px 12px;
  }

  .create-actions__summary {
    justify-content: space-between;
  }

  .create-actions__buttons {
    justify-content: flex-end;
  }
}
</style>
