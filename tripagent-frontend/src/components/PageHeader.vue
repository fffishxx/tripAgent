<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: Component
    stage?: string
  }>(),
  {
    description: '',
    icon: undefined,
    stage: '',
  },
)
</script>

<template>
  <header class="page-header">
    <div class="page-header__main">
      <span v-if="icon" class="page-header__icon">
        <el-icon><component :is="icon" /></el-icon>
      </span>
      <div class="page-header__copy">
        <div class="page-header__title-row">
          <h1>{{ title }}</h1>
          <span v-if="stage" class="stage-badge">{{ stage }}</span>
        </div>
        <p v-if="description">{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 6px 2px 0;
}

.page-header__main {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.page-header__icon {
  display: grid;
  flex: 0 0 auto;
  width: 52px;
  height: 52px;
  border: 1px solid rgba(47, 126, 247, 0.12);
  border-radius: 16px;
  color: var(--ta-primary-deep);
  font-size: 24px;
  place-items: center;
  background: linear-gradient(145deg, #f0f7ff, #deedff);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 10px 24px rgba(47, 126, 247, 0.1);
}

.page-header__copy {
  min-width: 0;
}

.page-header__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.page-header__title-row h1 {
  color: var(--ta-ink-strong);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.03em;
}

.page-header__copy p {
  margin-top: 4px;
  color: var(--ta-ink-secondary);
  font-size: 13px;
}

.page-header__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

@media (max-width: 760px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .page-header__icon {
    width: 46px;
    height: 46px;
    border-radius: 14px;
    font-size: 21px;
  }

  .page-header__title-row h1 {
    font-size: 24px;
  }
}
</style>
