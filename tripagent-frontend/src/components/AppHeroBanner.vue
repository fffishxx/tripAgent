<script setup lang="ts">
import { Calendar, Location, MagicStick, Promotion } from '@element-plus/icons-vue'
import SceneryPlaceholder from '@/components/SceneryPlaceholder.vue'
</script>

<template>
  <section class="hero">
    <SceneryPlaceholder variant="hero" label="山水背景占位" />

    <div class="hero__content">
      <span class="hero__eyebrow">
        <el-icon><MagicStick /></el-icon>
        AI 旅行规划助手
      </span>
      <h1>一场说走就走的旅行</h1>
      <p>让 TripAgent 为你规划完美行程，从灵感、路线到出行，一站完成。</p>
      <div class="hero__tags">
        <span><el-icon><MagicStick /></el-icon>智能规划</span>
        <span><el-icon><Location /></el-icon>地图探索</span>
        <span><el-icon><Calendar /></el-icon>行程管理</span>
      </div>
    </div>

    <el-icon class="hero__plane" aria-hidden="true"><Promotion /></el-icon>

    <div class="hero__status">
      <span class="hero__status-dot" />
      AI 对话已接入 · 随时开始规划
    </div>
  </section>
</template>

<style scoped>
.hero {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  min-height: 232px;
  padding: 34px 38px;
  border: 1px solid rgba(202, 224, 250, 0.9);
  border-radius: var(--ta-radius-xl);
  box-shadow: 0 22px 54px rgba(54, 112, 199, 0.1);
}

.hero__content {
  position: relative;
  z-index: 2;
  max-width: 660px;
}

.hero__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  color: var(--ta-primary-deep);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
}

.hero h1 {
  margin-top: 16px;
  color: #15335d;
  font-size: 38px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.04em;
  text-wrap: balance;
}

.hero p {
  max-width: 500px;
  margin-top: 10px;
  color: #5d7799;
  font-size: 14px;
  line-height: 1.8;
}

.hero__tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 20px;
}

.hero__tags span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 11px;
  border: 1px solid rgba(255, 255, 255, 0.64);
  border-radius: 9px;
  color: #4c6c92;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.44);
  backdrop-filter: blur(8px);
}

.hero__plane {
  position: absolute;
  z-index: 1;
  top: 46px;
  right: 12%;
  color: rgba(47, 126, 247, 0.28);
  font-size: 76px;
  transform: rotate(14deg);
}

.hero__status {
  position: absolute;
  z-index: 2;
  right: 32px;
  bottom: 30px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 12px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  color: #537099;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(8px);
}

.hero__status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ta-success);
  box-shadow: 0 0 0 3px rgba(39, 180, 122, 0.14);
}

@media (max-width: 900px) {
  .hero {
    min-height: 208px;
    padding: 28px 28px;
  }

  .hero h1 {
    font-size: 31px;
  }

  .hero__plane {
    top: 34px;
    right: 6%;
    font-size: 58px;
  }

  .hero__status {
    display: none;
  }
}

@media (max-width: 560px) {
  .hero {
    padding: 24px 22px;
  }

  .hero h1 {
    font-size: 27px;
  }

  .hero p {
    font-size: 13px;
  }

  .hero__plane {
    display: none;
  }
}
</style>
