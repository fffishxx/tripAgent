<script setup lang="ts">
import { Picture } from '@element-plus/icons-vue'

withDefaults(
  defineProps<{
    variant?: 'hero' | 'sidebar'
    label?: string
  }>(),
  {
    variant: 'hero',
    label: '山水素材占位',
  },
)
</script>

<template>
  <div class="scenery" :class="`scenery--${variant}`" aria-hidden="true">
    <span class="scenery__glow scenery__glow--one" />
    <span class="scenery__glow scenery__glow--two" />
    <span class="scenery__wave scenery__wave--back" />
    <span class="scenery__wave scenery__wave--front" />
    <span v-if="label" class="scenery__label">
      <el-icon><Picture /></el-icon>
      {{ label }}
    </span>
  </div>
</template>

<style scoped>
.scenery {
  position: relative;
  overflow: hidden;
  background:
    linear-gradient(145deg, rgba(236, 246, 255, 0.96), rgba(207, 231, 255, 0.92) 54%, rgba(247, 252, 255, 0.98)),
    #eaf4ff;
}

.scenery--hero {
  position: absolute;
  inset: 0;
  border-radius: inherit;
}

.scenery--sidebar {
  height: 154px;
  border: 1px dashed rgba(47, 126, 247, 0.22);
  border-radius: 18px;
  background:
    linear-gradient(150deg, rgba(235, 246, 255, 0.98), rgba(206, 230, 255, 0.88) 58%, rgba(246, 251, 255, 0.99)),
    #eaf4ff;
}

.scenery__glow {
  position: absolute;
  display: block;
  border-radius: 50%;
  filter: blur(2px);
}

.scenery__glow--one {
  top: -26%;
  right: 8%;
  width: 44%;
  aspect-ratio: 1;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.86), transparent 68%);
}

.scenery__glow--two {
  bottom: -38%;
  left: 6%;
  width: 36%;
  aspect-ratio: 1;
  background: radial-gradient(circle, rgba(120, 174, 250, 0.18), transparent 70%);
}

.scenery__wave {
  position: absolute;
  display: block;
  border-radius: 48% 52% 0 0 / 100% 100% 0 0;
}

.scenery__wave--back {
  right: -14%;
  bottom: -46%;
  width: 76%;
  height: 80%;
  background: rgba(112, 164, 240, 0.13);
  transform: rotate(-7deg);
}

.scenery__wave--front {
  bottom: -58%;
  left: -10%;
  width: 70%;
  height: 90%;
  background: rgba(72, 133, 224, 0.08);
  transform: rotate(5deg);
}

.scenery__label {
  position: absolute;
  right: 12px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border: 1px solid rgba(255, 255, 255, 0.58);
  border-radius: 999px;
  color: #7890b1;
  font-size: 11px;
  letter-spacing: 0.02em;
  background: rgba(255, 255, 255, 0.56);
  backdrop-filter: blur(6px);
}

.scenery--sidebar .scenery__label {
  right: 50%;
  bottom: 50%;
  transform: translate(50%, 50%);
  white-space: nowrap;
  background: rgba(255, 255, 255, 0.64);
}

@media (max-width: 560px) {
  .scenery--hero .scenery__label {
    display: none;
  }
}
</style>
