<script setup lang="ts">
import { CircleCheck } from '@element-plus/icons-vue'
import BrandLogo from '@/components/BrandLogo.vue'
import SceneryPlaceholder from '@/components/SceneryPlaceholder.vue'
</script>

<template>
  <div class="auth-page">
    <section class="auth-visual">
      <SceneryPlaceholder variant="hero" label="山水背景占位" />

      <div class="auth-visual__top">
        <BrandLogo />
      </div>

      <div class="auth-visual__copy">
        <span class="auth-visual__kicker">TRIPAGENT · AI TRAVEL PLANNER</span>
        <h1>下一次出发<br />交给 TripAgent 规划</h1>
        <p>
          对话式 AI 行程规划、路线校验、会话归档与模拟出行，一站完成你的旅行准备。
        </p>
        <ul class="auth-benefits">
          <li>
            <span class="auth-benefits__icon"><el-icon><CircleCheck /></el-icon></span>
            <span>
              <strong>对话式旅行规划</strong>
              <small>流式回复、结构化行程与路线提醒</small>
            </span>
          </li>
          <li>
            <span class="auth-benefits__icon"><el-icon><CircleCheck /></el-icon></span>
            <span>
              <strong>会话与行程归档</strong>
              <small>登录后自动保存，随时恢复未完成的规划</small>
            </span>
          </li>
          <li>
            <span class="auth-benefits__icon"><el-icon><CircleCheck /></el-icon></span>
            <span>
              <strong>清晰的权限边界</strong>
              <small>匿名可咨询，业务数据登录后保存</small>
            </span>
          </li>
        </ul>
      </div>

      <div class="auth-visual__footer">
        AI 行程规划 · 行程管理 · 地图探索 · 模拟打车
      </div>
    </section>

    <section class="auth-panel">
      <div class="auth-mobile-brand">
        <BrandLogo />
      </div>

      <div class="auth-card surface-card">
        <slot />
      </div>

      <p class="auth-panel__footnote">
        <slot name="footnote" />
      </p>
    </section>
  </div>
</template>
