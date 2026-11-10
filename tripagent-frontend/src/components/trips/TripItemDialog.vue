<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { TripDraftDay, TripDraftItem } from '@/types/trip'
import {
  ITEM_TYPE_OPTIONS,
  TIME_SLOT_OPTIONS,
  formatDayDate,
} from '@/utils/trip'
import { createId } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    mode?: 'create' | 'edit'
    item?: TripDraftItem | null
    days: TripDraftDay[]
    defaultDayIndex?: number
  }>(),
  {
    mode: 'create',
    item: null,
    defaultDayIndex: 1,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [item: TripDraftItem, targetDayIndex: number]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const formRef = ref<FormInstance>()
const submitting = ref(false)

interface ItemForm {
  dayIndex: number
  itemType: string
  name: string
  timeSlot: string
  durationMin: number | undefined
  address: string
  reason: string
  notes: string
  longitude: number | undefined
  latitude: number | undefined
}

const form = reactive<ItemForm>({
  dayIndex: 1,
  itemType: 'ATTRACTION',
  name: '',
  timeSlot: '上午',
  durationMin: 60,
  address: '',
  reason: '',
  notes: '',
  longitude: undefined,
  latitude: undefined,
})

const rules: FormRules<ItemForm> = {
  dayIndex: [{ required: true, message: '请选择所属日期', trigger: 'change' }],
  itemType: [{ required: true, message: '请选择安排类型', trigger: 'change' }],
  name: [
    { required: true, message: '请输入景点或安排名称', trigger: 'blur' },
    { max: 128, message: '名称不能超过 128 个字符', trigger: 'blur' },
  ],
  address: [{ max: 255, message: '地址不能超过 255 个字符', trigger: 'blur' }],
  reason: [{ max: 500, message: '安排理由不能超过 500 个字符', trigger: 'blur' }],
  notes: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }],
}

const dialogTitle = computed(() =>
  props.mode === 'edit' ? '编辑景点与安排' : '添加景点与安排',
)

const confirmLabel = computed(() =>
  props.mode === 'edit' ? '保存修改' : '添加到行程',
)

const dayOptions = computed(() => {
  const sourceDayIndex = props.item ? findDayIndexForItem(props.item) : null
  return props.days.map((day) => ({
    value: day.dayIndex,
    label: `第${day.dayIndex}天 · ${formatDayDate(day.dayDate)}`,
    disabled: day.items.length >= 20 && day.dayIndex !== sourceDayIndex,
  }))
})

function findDayIndexForItem(item: TripDraftItem): number {
  const day = props.days.find((candidate) =>
    candidate.items.some((candidateItem) => candidateItem.localId === item.localId),
  )
  return day?.dayIndex ?? props.defaultDayIndex
}

function resetForm(): void {
  const item = props.item
  form.dayIndex = item
    ? findDayIndexForItem(item)
    : Math.min(
        Math.max(props.defaultDayIndex, 1),
        Math.max(props.days.length, 1),
      )
  form.itemType = item?.itemType || 'ATTRACTION'
  form.name = item?.name ?? ''
  form.timeSlot = item ? item.timeSlot : '上午'
  form.durationMin = item ? (item.durationMin ?? undefined) : 60
  form.address = item?.address ?? ''
  form.reason = item?.reason ?? ''
  form.notes = item?.notes ?? ''
  form.longitude = item?.longitude ?? undefined
  form.latitude = item?.latitude ?? undefined
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      submitting.value = false
      resetForm()
      nextTick(() => formRef.value?.clearValidate())
    }
  },
)

function clearCoordinates(): void {
  form.longitude = undefined
  form.latitude = undefined
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value || submitting.value) {
    return
  }

  submitting.value = true
  try {
    let valid = false
    try {
      valid = await formRef.value.validate()
    } catch {
      valid = false
    }
    if (!valid) {
      return
    }

    const targetDay = props.days.find((day) => day.dayIndex === form.dayIndex)
    const sourceDayIndex = props.item ? findDayIndexForItem(props.item) : null
    if (
      targetDay &&
      targetDay.items.length >= 20 &&
      targetDay.dayIndex !== sourceDayIndex
    ) {
      ElMessage.warning(`第${targetDay.dayIndex}天的安排已达上限（20 个）`)
      return
    }

    const hasLongitude = typeof form.longitude === 'number'
    const hasLatitude = typeof form.latitude === 'number'
    if (hasLongitude !== hasLatitude) {
      ElMessage.warning('经纬度必须同时填写或全部留空')
      return
    }
    if (
      hasLongitude &&
      hasLatitude &&
      (form.longitude! < -180 ||
        form.longitude! > 180 ||
        form.latitude! < -90 ||
        form.latitude! > 90)
    ) {
      ElMessage.warning('经纬度超出合法范围')
      return
    }

    if (
      form.durationMin !== undefined &&
      (form.durationMin < 1 || form.durationMin > 1440)
    ) {
      ElMessage.warning('停留时长需在 1-1440 分钟之间')
      return
    }

    const base = props.item
    emit(
      'submit',
      {
        localId: base?.localId ?? createId('item-'),
        sourceId: base?.sourceId ?? null,
        itemType: form.itemType,
        name: form.name.trim(),
        address: form.address.trim(),
        longitude: hasLongitude ? form.longitude! : null,
        latitude: hasLatitude ? form.latitude! : null,
        timeSlot: form.timeSlot,
        durationMin: form.durationMin ?? null,
        reason: form.reason.trim(),
        notes: form.notes.trim(),
        externalPoiId: base?.externalPoiId ?? null,
      },
      form.dayIndex,
    )

    visible.value = false
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="min(620px, 92vw)"
    :close-on-click-modal="false"
    append-to-body
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="trip-item-form"
    >
      <div class="form-grid form-grid--2">
        <el-form-item label="所属日期" prop="dayIndex">
          <el-select v-model="form.dayIndex" class="form-control">
            <el-option
              v-for="option in dayOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
              :disabled="option.disabled"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="安排类型" prop="itemType">
          <el-select v-model="form.itemType" class="form-control">
            <el-option
              v-for="option in ITEM_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </div>

      <el-form-item label="景点 / 安排名称" prop="name">
        <el-input
          v-model="form.name"
          maxlength="128"
          show-word-limit
          placeholder="例如：西湖、灵隐寺"
        />
      </el-form-item>

      <div class="form-grid form-grid--2">
        <el-form-item label="时间段">
          <el-select
            v-model="form.timeSlot"
            clearable
            placeholder="未指定"
            class="form-control"
          >
            <el-option
              v-for="slot in TIME_SLOT_OPTIONS"
              :key="slot"
              :label="slot"
              :value="slot"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="停留时长（分钟）">
          <el-input-number
            v-model="form.durationMin"
            :min="1"
            :max="1440"
            :step="15"
            controls-position="right"
            class="form-control"
            placeholder="选填"
          />
        </el-form-item>
      </div>

      <el-form-item label="地址" prop="address">
        <el-input
          v-model="form.address"
          maxlength="255"
          placeholder="选填，例如：西湖区龙井路"
        />
      </el-form-item>

      <el-form-item label="安排理由" prop="reason">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          resize="none"
          placeholder="选填，例如：适合上午游览，交通方便"
        />
      </el-form-item>

      <el-form-item label="备注" prop="notes">
        <el-input
          v-model="form.notes"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          resize="none"
          placeholder="选填，例如：需要提前预约"
        />
      </el-form-item>

      <div class="coordinate-block">
        <div class="coordinate-block__head">
          <span>经纬度（选填）</span>
          <el-button
            v-if="
              form.longitude !== undefined || form.latitude !== undefined
            "
            text
            size="small"
            @click="clearCoordinates"
          >
            清空坐标
          </el-button>
        </div>
        <div class="form-grid form-grid--2">
          <el-input-number
            v-model="form.longitude"
            :min="-180"
            :max="180"
            :precision="6"
            :controls="false"
            class="form-control"
            placeholder="经度，例如 120.155070"
          />
          <el-input-number
            v-model="form.latitude"
            :min="-90"
            :max="90"
            :precision="6"
            :controls="false"
            class="form-control"
            placeholder="纬度，例如 30.274084"
          />
        </div>
        <p class="coordinate-block__hint">
          经纬度需同时填写或同时留空；后续地图路线会优先使用坐标。
        </p>
      </div>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">
        取消
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="submitting"
        @click="handleSubmit"
      >
        {{ confirmLabel }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.trip-item-form {
  padding-top: 2px;
}

.form-grid {
  display: grid;
  gap: 14px;
}

.form-grid--2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-control {
  width: 100%;
}

.trip-item-form :deep(.el-form-item__label) {
  padding-bottom: 5px;
  color: var(--ta-ink-secondary);
  font-size: 12px;
  font-weight: 500;
}

.trip-item-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.coordinate-block {
  padding: 12px;
  border: 1px dashed #dce9f8;
  border-radius: 12px;
  background: #fafdff;
}

.coordinate-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--ta-ink-secondary);
  font-size: 12px;
  font-weight: 500;
}

.coordinate-block__hint {
  margin-top: 7px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.6;
}

@media (max-width: 620px) {
  .form-grid--2 {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
