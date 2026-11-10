<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    label: string
    value?: string
    hint?: string
    icon?: Component
    tone?: 'blue' | 'green' | 'amber' | 'violet'
  }>(),
  {
    value: '--',
    hint: '',
    icon: undefined,
    tone: 'blue',
  },
)
</script>

<template>
  <article class="stat-card" :class="`stat-card--${tone}`">
    <span v-if="icon" class="stat-card__icon">
      <el-icon><component :is="icon" /></el-icon>
    </span>
    <div class="stat-card__body">
      <strong>{{ value }}</strong>
      <span>{{ label }}</span>
      <small v-if="hint">{{ hint }}</small>
    </div>
  </article>
</template>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
  padding: 20px 22px;
  border: 1px solid rgba(219, 234, 250, 0.92);
  border-radius: var(--ta-radius-md);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: var(--ta-shadow-soft);
}

.stat-card__icon {
  display: grid;
  flex: 0 0 auto;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  font-size: 21px;
  place-items: center;
}

.stat-card--blue .stat-card__icon {
  color: #2f7ef7;
  background: #eaf3ff;
}

.stat-card--green .stat-card__icon {
  color: #23a873;
  background: #e8f8f1;
}

.stat-card--amber .stat-card__icon {
  color: #d99021;
  background: #fff5e5;
}

.stat-card--violet .stat-card__icon {
  color: #7c6cf0;
  background: #f0edff;
}

.stat-card__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stat-card__body strong {
  color: var(--ta-ink-strong);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.stat-card__body span {
  margin-top: 1px;
  color: var(--ta-ink-secondary);
  font-size: 13px;
}

.stat-card__body small {
  margin-top: 2px;
  color: var(--ta-ink-muted);
  font-size: 11px;
}
</style>
