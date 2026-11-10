<script setup lang="ts">
import { ref, watch } from 'vue'
import { Location, WarningFilled } from '@element-plus/icons-vue'
import { searchPois } from '@/api/amap'
import type { TaxiLocation } from '@/types/taxi'

type LocationSuggestion = {
  value: string
  name: string
  address: string
  city: string
  longitude: number
  latitude: number
}

const props = withDefaults(
  defineProps<{
    modelValue: TaxiLocation | null
    city?: string
    placeholder?: string
  }>(),
  {
    city: '',
    placeholder: '搜索并选择地点',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: TaxiLocation | null]
}>()

const keyword = ref(props.modelValue?.name ?? '')
const selected = ref<TaxiLocation | null>(props.modelValue)
const suggestionError = ref('')

watch(
  () => props.modelValue,
  (value) => {
    selected.value = value
    keyword.value = value?.name ?? ''
  },
)

watch(keyword, (value) => {
  suggestionError.value = ''
  if (selected.value && value !== selected.value.name) {
    selected.value = null
    emit('update:modelValue', null)
  }
})

async function fetchSuggestions(
  query: string,
  callback: (items: LocationSuggestion[]) => void,
): Promise<void> {
  const text = query.trim()
  if (!text) {
    suggestionError.value = ''
    callback([])
    return
  }

  try {
    const result = await searchPois({
      keywords: text,
      city: props.city,
      pageSize: 5,
    })
    suggestionError.value = ''
    callback(
      result.pois.map((poi) => ({
        value: poi.name,
        name: poi.name,
        address: poi.address,
        city: poi.city || poi.district,
        longitude: poi.longitude,
        latitude: poi.latitude,
      })),
    )
  } catch {
    suggestionError.value = '地点搜索失败，请检查网络后重试'
    callback([])
  }
}

function handleSelect(item: LocationSuggestion): void {
  const location: TaxiLocation = {
    name: item.name,
    address: item.address,
    longitude: item.longitude,
    latitude: item.latitude,
    city: item.city,
  }
  selected.value = location
  keyword.value = location.name
  emit('update:modelValue', location)
}

function handleClear(): void {
  selected.value = null
  keyword.value = ''
  emit('update:modelValue', null)
}
</script>

<template>
  <div class="location-picker">
    <el-autocomplete
      v-model="keyword"
      :fetch-suggestions="fetchSuggestions"
      :debounce="350"
      :trigger-on-focus="false"
      :prefix-icon="Location"
      clearable
      class="location-picker__input"
      :placeholder="placeholder"
      @select="handleSelect"
      @clear="handleClear"
    >
      <template #default="{ item }">
        <div class="location-option">
          <span class="location-option__icon">
            <el-icon><Location /></el-icon>
          </span>
          <span class="location-option__body">
            <strong>{{ item.name }}</strong>
            <small>{{ item.address || item.city || '暂无详细地址' }}</small>
          </span>
        </div>
      </template>
    </el-autocomplete>

    <p v-if="suggestionError" class="location-picker__error">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ suggestionError }}</span>
    </p>
    <p v-else-if="selected" class="location-picker__selected">
      <el-icon><Location /></el-icon>
      <span>
        {{ selected.city || '城市待确认' }}
        <template v-if="selected.address"> · {{ selected.address }}</template>
      </span>
    </p>
    <p v-else class="location-picker__hint">
      从高德地点建议中选择，才能获得经纬度用于模拟估价。
    </p>
  </div>
</template>

<style scoped>
.location-picker {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.location-picker__input {
  width: 100%;
}

.location-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 2px;
}

.location-option__icon {
  display: grid;
  flex: 0 0 auto;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  color: var(--ta-primary);
  font-size: 13px;
  place-items: center;
  background: #eaf4ff;
}

.location-option__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.location-option__body strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 11.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.location-option__body small {
  overflow: hidden;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.location-picker__selected,
.location-picker__hint,
.location-picker__error {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  min-height: 16px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.6;
}

.location-picker__selected {
  color: #4d7d6b;
}

.location-picker__error {
  color: #b15c50;
}

.location-picker__selected .el-icon,
.location-picker__error .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}
</style>
