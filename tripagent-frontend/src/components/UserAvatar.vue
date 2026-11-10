<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getAvatarText } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    name: string
    src?: string | null
    size?: number
    shape?: 'circle' | 'rounded'
  }>(),
  {
    src: null,
    size: 64,
    shape: 'circle',
  },
)

const imageFailed = ref(false)

const imageVisible = computed(() => Boolean(props.src) && !imageFailed.value)
const initials = computed(() => getAvatarText(props.name))

watch(
  () => props.src,
  () => {
    imageFailed.value = false
  },
)
</script>

<template>
  <span
    class="user-avatar"
    :class="`user-avatar--${shape}`"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      fontSize: `${Math.max(11, Math.round(size * 0.38))}px`,
    }"
  >
    <img
      v-if="imageVisible"
      :src="props.src ?? ''"
      alt=""
      referrerpolicy="no-referrer"
      @error="imageFailed = true"
    />
    <span v-else>{{ initials }}</span>
  </span>
</template>

<style scoped>
.user-avatar {
  display: inline-grid;
  flex: 0 0 auto;
  overflow: hidden;
  color: #fff;
  font-weight: 600;
  line-height: 1;
  place-items: center;
  background: linear-gradient(145deg, #63a6ff, #2a6ee8);
  box-shadow: 0 8px 18px rgba(47, 126, 247, 0.2);
}

.user-avatar--circle {
  border-radius: 50%;
}

.user-avatar--rounded {
  border-radius: 32%;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
