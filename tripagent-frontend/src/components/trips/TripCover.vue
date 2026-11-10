<script setup lang="ts">
import { Promotion } from '@element-plus/icons-vue'

withDefaults(
  defineProps<{
    destination: string
    size?: 'small' | 'large'
    label?: string
  }>(),
  {
    size: 'small',
    label: '目的地',
  },
)
</script>

<template>
  <div class="trip-cover" :class="`trip-cover--${size}`" aria-hidden="true">
    <span class="trip-cover__glow trip-cover__glow--one" />
    <span class="trip-cover__glow trip-cover__glow--two" />
    <span class="trip-cover__wave" />
    <el-icon class="trip-cover__plane"><Promotion /></el-icon>
    <span class="trip-cover__caption">
      <small>{{ label }}</small>
      <strong>{{ destination || '旅行目的地' }}</strong>
    </span>
  </div>
</template>

<style scoped>
.trip-cover {
  position: relative;
  overflow: hidden;
  flex: 0 0 auto;
  border: 1px solid rgba(203, 224, 249, 0.9);
  border-radius: 15px;
  color: #244f80;
  background:
    linear-gradient(145deg, #e8f4ff 0%, #d5e9ff 48%, #f5fbff 100%),
    #e8f4ff;
}

.trip-cover--small {
  width: 156px;
  height: 108px;
}

.trip-cover--large {
  width: 218px;
  height: 146px;
  border-radius: 18px;
}

.trip-cover__glow {
  position: absolute;
  display: block;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.55);
}

.trip-cover__glow--one {
  top: -26%;
  right: -8%;
  width: 58%;
  aspect-ratio: 1;
}

.trip-cover__glow--two {
  bottom: -30%;
  left: -14%;
  width: 62%;
  aspect-ratio: 1;
  background: rgba(117, 172, 249, 0.15);
}

.trip-cover__wave {
  position: absolute;
  right: -12%;
  bottom: -48%;
  width: 78%;
  height: 82%;
  border-radius: 48% 52% 0 0 / 100% 100% 0 0;
  background: rgba(78, 137, 225, 0.1);
  transform: rotate(-8deg);
}

.trip-cover__plane {
  position: absolute;
  top: 14%;
  right: 11%;
  color: rgba(47, 126, 247, 0.32);
  font-size: 26px;
  transform: rotate(12deg);
}

.trip-cover--large .trip-cover__plane {
  top: 12%;
  font-size: 36px;
}

.trip-cover__caption {
  position: absolute;
  right: 14px;
  bottom: 12px;
  left: 14px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.trip-cover__caption small {
  color: #7592b4;
  font-size: 9px;
  letter-spacing: 0.12em;
}

.trip-cover__caption strong {
  overflow: hidden;
  color: #20466f;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-cover--large .trip-cover__caption {
  right: 18px;
  bottom: 16px;
  left: 18px;
}

.trip-cover--large .trip-cover__caption strong {
  font-size: 22px;
}

@media (max-width: 760px) {
  .trip-cover--small {
    width: 104px;
    height: 82px;
    border-radius: 12px;
  }

  .trip-cover--small .trip-cover__caption {
    right: 9px;
    bottom: 8px;
    left: 9px;
  }

  .trip-cover--small .trip-cover__caption strong {
    font-size: 13px;
  }

  .trip-cover--small .trip-cover__caption small {
    font-size: 8px;
  }

  .trip-cover--large {
    width: 100%;
    height: 128px;
  }
}
</style>
