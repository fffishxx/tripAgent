<script setup lang="ts">
import type { Component } from 'vue'
import { Box } from '@element-plus/icons-vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: Component
    stage?: string
  }>(),
  {
    description: '',
    icon: Box,
    stage: '',
  },
)
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__art">
      <span class="empty-state__ring empty-state__ring--one" />
      <span class="empty-state__ring empty-state__ring--two" />
      <span class="empty-state__icon">
        <el-icon><component :is="icon" /></el-icon>
      </span>
    </div>
    <div class="empty-state__title-row">
      <h2>{{ title }}</h2>
      <span v-if="stage" class="stage-badge">{{ stage }}</span>
    </div>
    <p v-if="description">{{ description }}</p>
    <div v-if="$slots.default" class="empty-state__actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 10px;
  padding: 58px 24px 64px;
  text-align: center;
}

.empty-state__art {
  position: relative;
  display: grid;
  width: 132px;
  height: 132px;
  margin-bottom: 6px;
  place-items: center;
}

.empty-state__art::before {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  content: '';
  background: radial-gradient(circle, rgba(47, 126, 247, 0.14), transparent 68%);
}

.empty-state__ring {
  position: absolute;
  display: block;
  border: 1px dashed rgba(47, 126, 247, 0.2);
  border-radius: 50%;
}

.empty-state__ring--one {
  inset: 4px;
}

.empty-state__ring--two {
  inset: 22px;
  border-style: solid;
  border-color: rgba(47, 126, 247, 0.08);
}

.empty-state__icon {
  position: relative;
  display: grid;
  width: 62px;
  height: 62px;
  border: 1px solid rgba(47, 126, 247, 0.1);
  border-radius: 21px;
  color: var(--ta-primary);
  font-size: 28px;
  place-items: center;
  background: linear-gradient(145deg, #f5faff, #e2efff);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.95),
    0 14px 30px rgba(47, 126, 247, 0.16);
  transform: rotate(-4deg);
}

.empty-state__title-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  flex-wrap: wrap;
}

.empty-state__title-row h2 {
  color: var(--ta-ink-strong);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.empty-state p {
  max-width: 430px;
  color: var(--ta-ink-muted);
  font-size: 13px;
  line-height: 1.8;
}

.empty-state__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
</style>
