<script setup lang="ts">
import { useId } from 'vue'

withDefaults(
  defineProps<{
    compact?: boolean
  }>(),
  {
    compact: false,
  },
)

// 同一页面可能渲染多个 Logo（如登录页的桌面/移动端品牌位），
// 使用唯一 ID 避免 SVG 渐变引用冲突。
const gradientId = `tripagent-brand-gradient-${useId()}`
</script>

<template>
  <RouterLink
    to="/chat"
    class="brand"
    :class="{ 'brand--compact': compact }"
    aria-label="TripAgent 首页"
  >
    <span class="brand__mark" aria-hidden="true">
      <svg viewBox="0 0 40 40" fill="none">
        <defs>
          <linearGradient
            :id="gradientId"
            x1="4"
            y1="2"
            x2="36"
            y2="40"
            gradientUnits="userSpaceOnUse"
          >
            <stop stop-color="#67adff" />
            <stop offset="1" stop-color="#2163dc" />
          </linearGradient>
        </defs>
        <rect width="40" height="40" rx="13" :fill="`url(#${gradientId})`" />
        <path d="M9.5 21.1 31 11.2l-5.4 18.2-5.6-7.5-10.5-0.8Z" fill="#fff" />
        <path
          d="M20 21.9 31 11.2"
          stroke="#d8e8ff"
          stroke-width="1.5"
          stroke-linecap="round"
        />
      </svg>
    </span>
    <span v-if="!compact" class="brand__text">
      <strong>TripAgent</strong>
      <small>你的智能旅行助手</small>
    </span>
  </RouterLink>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.brand__mark {
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  filter: drop-shadow(0 8px 14px rgba(47, 126, 247, 0.22));
}

.brand__mark svg {
  width: 100%;
  height: 100%;
}

.brand__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.25;
}

.brand__text strong {
  color: var(--ta-ink-strong);
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.brand__text small {
  margin-top: 2px;
  overflow: hidden;
  color: var(--ta-ink-muted);
  font-size: 11px;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.brand--compact {
  justify-content: center;
}
</style>
