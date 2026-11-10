<script setup lang="ts">
import { Lock, MagicStick, WarningFilled } from '@element-plus/icons-vue'
import TaxiCard from '@/components/chat/TaxiCard.vue'
import TripPlanCard from '@/components/chat/TripPlanCard.vue'
import { useAuthStore } from '@/stores/auth'
import type {
  AgentTaxiVehicleOption,
  ChatMessage,
} from '@/types/agent'

const props = withDefaults(
  defineProps<{
    message: ChatMessage
    selectedVehicle?: string | null
    vehicleDisabled?: boolean
  }>(),
  {
    selectedVehicle: null,
    vehicleDisabled: false,
  },
)

const emit = defineEmits<{
  selectVehicle: [option: AgentTaxiVehicleOption]
  login: []
}>()

const auth = useAuthStore()

function handleSelectVehicle(option: AgentTaxiVehicleOption): void {
  emit('selectVehicle', option)
}
</script>

<template>
  <div
    v-if="props.message.role === 'system'"
    class="system-row"
    :class="`system-row--${props.message.variant || 'info'}`"
  >
    <div v-if="props.message.variant === 'login'" class="login-prompt">
      <span class="login-prompt__icon">
        <el-icon><Lock /></el-icon>
      </span>
      <div class="login-prompt__body">
        <strong>需要登录</strong>
        <p>{{ props.message.content }}</p>
      </div>
      <el-button type="primary" size="small" round @click="emit('login')">
        去登录
      </el-button>
    </div>

    <div v-else class="system-row__content">
      <el-icon v-if="props.message.variant === 'error'">
        <WarningFilled />
      </el-icon>
      <span>{{ props.message.content }}</span>
    </div>
  </div>

  <div
    v-else
    class="chat-message"
    :class="`chat-message--${props.message.role}`"
  >
    <span
      v-if="props.message.role === 'assistant'"
      class="chat-message__avatar"
    >
      <el-icon><MagicStick /></el-icon>
    </span>

    <div class="chat-message__body">
      <div class="chat-message__bubble">
        <p v-if="props.message.content" class="chat-message__text">
          {{ props.message.content
          }}<span
            v-if="props.message.streaming"
            class="chat-message__caret"
            aria-hidden="true"
          />
        </p>

        <span
          v-if="
            props.message.streaming &&
            !props.message.content &&
            !props.message.tripPlan &&
            !props.message.taxiOrder
          "
          class="typing-dots"
          aria-label="正在生成"
        >
          <i />
          <i />
          <i />
        </span>

        <TripPlanCard
          v-if="props.message.tripPlan"
          :plan="props.message.tripPlan"
          :validation="props.message.validation"
        />

        <TaxiCard
          v-if="props.message.taxiOrder"
          :card="props.message.taxiOrder"
          :disabled="props.vehicleDisabled"
          :selected-vehicle="props.selectedVehicle"
          @select="handleSelectVehicle"
        />
      </div>
    </div>

    <span
      v-if="props.message.role === 'user'"
      class="chat-message__avatar chat-message__avatar--user"
    >
      {{ auth.avatarText }}
    </span>
  </div>
</template>

<style scoped>
.chat-message {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 22px;
}

.chat-message:last-child {
  margin-bottom: 0;
}

.chat-message--user {
  flex-direction: row-reverse;
}

.chat-message__avatar {
  display: grid;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  color: var(--ta-primary);
  font-size: 17px;
  place-items: center;
  background: var(--ta-primary-soft);
}

.chat-message__avatar--user {
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  background: linear-gradient(145deg, #63a6ff, #2a6ee8);
  box-shadow: 0 6px 14px rgba(47, 126, 247, 0.18);
}

.chat-message__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: min(880px, calc(100% - 46px));
  min-width: 0;
}

.chat-message--user .chat-message__body {
  align-items: flex-end;
  max-width: min(72%, 680px);
}

.chat-message__bubble {
  min-width: 0;
  padding: 13px 16px;
  border: 1px solid #e7f0fb;
  border-radius: 6px 18px 18px 18px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 24px rgba(47, 100, 170, 0.05);
}

.chat-message--user .chat-message__bubble {
  border-color: #d7e7fb;
  border-radius: 18px 6px 18px 18px;
  background: linear-gradient(135deg, #eaf4ff, #dcecff);
  box-shadow: none;
}

.chat-message__text {
  color: var(--ta-ink-secondary);
  font-size: 13px;
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.chat-message--user .chat-message__text {
  color: var(--ta-ink);
}

.chat-message__caret {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  border-radius: 2px;
  vertical-align: -2px;
  background: var(--ta-primary);
  animation: caret-blink 1s steps(2, start) infinite;
}

.typing-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 2px;
}

.typing-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9bbce5;
  animation: typing-bounce 1.2s ease-in-out infinite;
}

.typing-dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.typing-dots i:nth-child(3) {
  animation-delay: 0.3s;
}

.system-row {
  display: flex;
  justify-content: center;
  margin: -4px 0 20px;
}

.system-row__content {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 90%;
  padding: 6px 12px;
  border: 1px solid #edf4fc;
  border-radius: 999px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  line-height: 1.6;
  text-align: center;
  background: rgba(255, 255, 255, 0.72);
}

.system-row--error .system-row__content {
  border-color: #f5e2dd;
  color: #b05f50;
  background: #fff8f6;
}

.login-prompt {
  display: flex;
  align-items: center;
  gap: 12px;
  width: min(540px, 100%);
  padding: 13px 15px;
  border: 1px solid #d8e8fb;
  border-radius: 14px;
  background: linear-gradient(135deg, #f5faff, #eef6ff);
}

.login-prompt__icon {
  display: grid;
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  color: var(--ta-primary);
  font-size: 17px;
  place-items: center;
  background: #fff;
}

.login-prompt__body {
  flex: 1;
  min-width: 0;
}

.login-prompt__body strong {
  display: block;
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
}

.login-prompt__body p {
  margin-top: 1px;
  color: var(--ta-ink-secondary);
  font-size: 11px;
  line-height: 1.6;
}

@keyframes caret-blink {
  0%,
  45% {
    opacity: 1;
  }
  46%,
  100% {
    opacity: 0;
  }
}

@keyframes typing-bounce {
  0%,
  60%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@media (max-width: 720px) {
  .chat-message {
    gap: 8px;
    margin-bottom: 18px;
  }

  .chat-message__avatar {
    width: 30px;
    height: 30px;
    border-radius: 9px;
    font-size: 15px;
  }

  .chat-message__avatar--user {
    font-size: 12px;
  }

  .chat-message__body {
    max-width: calc(100% - 38px);
  }

  .chat-message--user .chat-message__body {
    max-width: calc(100% - 38px);
  }

  .chat-message__bubble {
    padding: 11px 13px;
  }

  .login-prompt {
    align-items: flex-start;
    flex-wrap: wrap;
  }
}
</style>
