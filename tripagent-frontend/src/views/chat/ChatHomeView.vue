<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Clock,
  Lock,
  MagicStick,
  Plus,
  Promotion,
  WarningFilled,
} from '@element-plus/icons-vue'
import AppHeroBanner from '@/components/AppHeroBanner.vue'
import ChatMessageItem from '@/components/chat/ChatMessageItem.vue'
import { useAgentChat } from '@/composables/useAgentChat'
import { useAuthStore } from '@/stores/auth'
import type {
  AgentExample,
  AgentTaxiVehicleOption,
  ChatMessage,
} from '@/types/agent'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const {
  messages,
  isInitializing,
  isStreaming,
  sessionId,
  suggestedExamples,
  welcomeTitle,
  welcomeText,
  loadError,
  hasMessages,
  initialSessionId,
  scrollTick,
  bootstrap,
  retryLoad,
  startNewChat,
  sendMessage,
  stopGeneration,
} = useAgentChat()

const draft = ref('')
const isComposing = ref(false)
const streamRef = ref<HTMLElement | null>(null)
const vehicleSelections = reactive<Record<string, string>>({})
let scrollFrame = 0

const showHero = computed(
  () => !hasMessages.value && !initialSessionId && !loadError.value,
)

const isSessionRestored = computed(() => Boolean(initialSessionId))

const displayTitle = computed(() => {
  const title = route.query.title
  if (typeof title === 'string' && title.trim()) {
    return title.trim()
  }
  return isSessionRestored.value ? '历史会话' : 'TripAgent 助手'
})

const statusText = computed(() => {
  if (loadError.value) {
    return '会话加载失败'
  }
  if (isInitializing.value) {
    return '正在准备会话…'
  }
  if (isStreaming.value) {
    return '正在生成回复…'
  }
  return 'AI 规划已就绪'
})

const sessionPlaceholder = computed(() =>
  auth.isLoggedIn
    ? '请输入你的旅行需求…'
    : '请输入你的旅行需求（匿名会话不会保存历史）…',
)

const composerDisabled = computed(
  () => isInitializing.value || Boolean(loadError.value) || !sessionId.value,
)

const canSend = computed(
  () =>
    Boolean(draft.value.trim()) &&
    !isInitializing.value &&
    !isStreaming.value &&
    Boolean(sessionId.value),
)

function scheduleScroll(): void {
  if (scrollFrame) {
    return
  }
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = 0
    const element = streamRef.value
    if (element) {
      element.scrollTop = element.scrollHeight
    }
  })
}

watch(
  scrollTick,
  async () => {
    await nextTick()
    if (hasMessages.value) {
      scheduleScroll()
    }
  },
  { flush: 'post' },
)

watch(
  isSessionRestored,
  () => {
    if (isSessionRestored.value) {
      document.title = `${displayTitle.value} · TripAgent`
    }
  },
  { immediate: true },
)

onMounted(() => {
  void bootstrap()
})

async function handleSend(): Promise<void> {
  const question = draft.value.trim()
  if (!question || isStreaming.value || isInitializing.value) {
    return
  }

  draft.value = ''
  const sent = await sendMessage(question)
  if (!sent && !draft.value.trim()) {
    draft.value = question
  }
}

function handleEnter(event: Event): void {
  const keyboardEvent = event as KeyboardEvent
  if (keyboardEvent.isComposing || isComposing.value) {
    return
  }
  keyboardEvent.preventDefault()
  void handleSend()
}

async function handleSuggestion(example: AgentExample): Promise<void> {
  if (isStreaming.value || isInitializing.value) {
    return
  }
  draft.value = ''
  const sent = await sendMessage(example.describe)
  if (!sent && !draft.value.trim()) {
    draft.value = example.describe
  }
}

async function handleNewChat(): Promise<void> {
  if (isInitializing.value) {
    return
  }

  if (isStreaming.value) {
    try {
      await ElMessageBox.confirm(
        '当前回复正在生成，新建对话会停止当前生成。',
        '新建对话？',
        {
          confirmButtonText: '新建对话',
          cancelButtonText: '继续当前对话',
          type: 'warning',
        },
      )
    } catch {
      return
    }
  }

  if (isSessionRestored.value) {
    await router.push({ name: 'chat-home' })
    return
  }

  draft.value = ''
  await startNewChat()
}

watch(
  () => route.query.newChat,
  (value, oldValue) => {
    if (!value || value === oldValue) {
      return
    }
    void handleNewChat()
  },
)

async function handleSelectVehicle(
  message: ChatMessage,
  option: AgentTaxiVehicleOption,
): Promise<void> {
  if (isStreaming.value || vehicleSelections[message.id]) {
    return
  }
  vehicleSelections[message.id] = option.name
  const sent = await sendMessage(option.name)
  if (!sent) {
    delete vehicleSelections[message.id]
  }
}

function goToLogin(): void {
  if (auth.isLoggedIn) {
    auth.clearSession()
  }
  void router.push({
    name: 'login',
    query: {
      redirect: route.fullPath,
    },
  })
}

function goHistory(): void {
  void router.push({ name: 'history' })
}
</script>

<template>
  <div class="page chat-page">
    <AppHeroBanner v-if="showHero" />

    <section class="chat-panel surface-card">
      <header class="chat-panel__head">
        <div class="assistant">
          <span class="assistant__avatar">
            <el-icon><MagicStick /></el-icon>
          </span>
          <div class="assistant__meta">
            <div class="assistant__name-row">
              <strong>{{ displayTitle }}</strong>
              <span v-if="isSessionRestored" class="assistant__restored">
                已恢复
              </span>
              <span v-else-if="!auth.isLoggedIn" class="assistant__anonymous">
                匿名会话
              </span>
            </div>
            <span class="assistant__status">
              <i class="online-dot" />
              {{ statusText }}
            </span>
          </div>
        </div>

        <div class="chat-panel__actions">
          <el-button
            size="small"
            :icon="Plus"
            :disabled="isInitializing"
            @click="handleNewChat"
          >
            新对话
          </el-button>
          <el-button size="small" text :icon="Clock" @click="goHistory">
            历史会话
          </el-button>
        </div>
      </header>

      <div
        ref="streamRef"
        class="chat-stream"
        :class="{ 'chat-stream--conversation': hasMessages }"
      >
        <div v-if="isInitializing" class="chat-state">
          <span class="chat-state__spinner" aria-hidden="true" />
          <strong>正在准备会话</strong>
          <p>正在连接 TripAgent 助手，请稍候…</p>
        </div>

        <div v-else-if="loadError" class="chat-state chat-state--error">
          <span class="chat-state__icon">
            <el-icon><WarningFilled /></el-icon>
          </span>
          <strong>会话加载失败</strong>
          <p>{{ loadError }}</p>
          <div class="chat-state__actions">
            <el-button
              type="primary"
              round
              :loading="isInitializing"
              :disabled="isInitializing"
              @click="retryLoad"
            >
              重新加载
            </el-button>
            <el-button v-if="isSessionRestored" text @click="goHistory">
              返回历史会话
            </el-button>
          </div>
        </div>

        <div v-else-if="!hasMessages" class="chat-welcome">
          <div class="welcome-message">
            <span class="welcome-message__avatar">
              <el-icon><MagicStick /></el-icon>
            </span>
            <div class="welcome-message__bubble">
              <h2>{{ welcomeTitle }}</h2>
              <p>{{ welcomeText }}</p>

              <div v-if="!auth.isLoggedIn" class="anonymous-notice">
                <el-icon><Lock /></el-icon>
                <span>当前为匿名会话，历史不会保存。</span>
                <button type="button" class="text-button" @click="goToLogin">
                  登录后可保存
                </button>
              </div>

              <div class="prompt-grid">
                <button
                  v-for="example in suggestedExamples"
                  :key="`${example.title}-${example.describe}`"
                  type="button"
                  class="prompt-card"
                  @click="handleSuggestion(example)"
                >
                  <span>{{ example.title }}</span>
                  <small>{{ example.describe }}</small>
                  <el-icon class="prompt-card__arrow"><ArrowRight /></el-icon>
                </button>
              </div>
            </div>
          </div>
        </div>

        <template v-else>
          <ChatMessageItem
            v-for="message in messages"
            :key="message.id"
            :message="message"
            :selected-vehicle="vehicleSelections[message.id] ?? null"
            :vehicle-disabled="isStreaming"
            @select-vehicle="handleSelectVehicle(message, $event)"
            @login="goToLogin"
          />
        </template>
      </div>

      <footer class="composer">
        <div class="composer__box">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="2000"
            :disabled="composerDisabled"
            :placeholder="sessionPlaceholder"
            @keydown.enter.exact="handleEnter"
            @compositionstart="isComposing = true"
            @compositionend="isComposing = false"
          />

          <div class="composer__actions">
            <el-tooltip v-if="isStreaming" content="停止生成" placement="top">
              <el-button
                class="composer__stop"
                type="primary"
                plain
                circle
                aria-label="停止生成"
                @click="stopGeneration"
              >
                <span class="stop-square" />
              </el-button>
            </el-tooltip>

            <el-button
              v-else
              class="composer__send"
              type="primary"
              circle
              :disabled="!canSend"
              aria-label="发送消息"
              @click="handleSend"
            >
              <el-icon><Promotion /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="composer__meta">
          <span v-if="isStreaming" class="composer__hint">
            正在生成回复，可随时停止
          </span>
          <span v-else-if="!auth.isLoggedIn" class="composer__hint">
            <el-icon><Lock /></el-icon>
            匿名会话不会保存；
            <button type="button" class="text-button" @click="goToLogin">
              登录
            </button>
            后可保存行程与历史
          </span>
          <span v-else class="composer__hint">
            Enter 发送 · Shift + Enter 换行
          </span>
          <span class="composer__disclaimer">AI 生成内容仅供参考</span>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.chat-page {
  width: 100%;
  max-width: 1220px;
  margin: 0 auto;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 22px;
  border-bottom: 1px solid #eef5fd;
  background: rgba(255, 255, 255, 0.74);
}

.assistant {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.assistant__avatar {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  border-radius: 13px;
  color: #fff;
  font-size: 20px;
  place-items: center;
  background: linear-gradient(145deg, #6aabff, #2d72e7);
  box-shadow: 0 10px 20px rgba(47, 126, 247, 0.22);
}

.assistant__meta {
  min-width: 0;
}

.assistant__name-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.assistant__name-row strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant__restored,
.assistant__anonymous {
  padding: 1px 6px;
  border-radius: 5px;
  color: var(--ta-primary);
  font-size: 9px;
  white-space: nowrap;
  background: #eaf4ff;
}

.assistant__anonymous {
  color: #8a6d34;
  background: #fff6e7;
}

.assistant__status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 1px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.online-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ta-success);
  box-shadow: 0 0 0 3px rgba(39, 180, 122, 0.14);
}

.chat-panel__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
}

.chat-stream {
  min-height: 280px;
  padding: 26px 26px 18px;
  background: radial-gradient(circle at 12% 0%, rgba(224, 240, 255, 0.72), transparent 48%);
}

.chat-stream--conversation {
  max-height: calc(100vh - 380px);
  overflow-y: auto;
  scroll-behavior: smooth;
}

.chat-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 5px;
  padding: 54px 20px;
  text-align: center;
}

.chat-state__spinner {
  width: 30px;
  height: 30px;
  margin-bottom: 8px;
  border: 2px solid #dceafc;
  border-top-color: var(--ta-primary);
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.chat-state strong {
  color: var(--ta-ink);
  font-size: 14px;
  font-weight: 600;
}

.chat-state p {
  max-width: 430px;
  color: var(--ta-ink-muted);
  font-size: 12px;
  line-height: 1.7;
}

.chat-state--error p {
  color: #a26a5d;
}

.chat-state__icon {
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 8px;
  border-radius: 15px;
  color: #c9821d;
  font-size: 22px;
  place-items: center;
  background: #fff6e8;
}

.chat-state__actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.chat-welcome {
  padding: 4px 0 10px;
}

.welcome-message {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  max-width: 900px;
}

.welcome-message__avatar {
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

.welcome-message__bubble {
  padding: 20px 22px;
  border: 1px solid var(--ta-border);
  border-radius: 6px 20px 20px 20px;
  background: rgba(255, 255, 255, 0.9);
}

.welcome-message__bubble h2 {
  color: var(--ta-ink);
  font-size: 16px;
  font-weight: 600;
}

.welcome-message__bubble > p {
  margin-top: 6px;
  color: var(--ta-ink-secondary);
  font-size: 13px;
  line-height: 1.8;
}

.anonymous-notice {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding: 8px 10px;
  border: 1px dashed #efdbb8;
  border-radius: 9px;
  color: #8a6d34;
  font-size: 11px;
  background: #fffbf3;
}

.text-button {
  padding: 0;
  border: 0;
  color: var(--ta-primary);
  font-size: inherit;
  background: transparent;
  cursor: pointer;
}

.text-button:hover {
  color: var(--ta-primary-deep);
  text-decoration: underline;
}

.prompt-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.prompt-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 3px;
  padding: 12px 30px 12px 14px;
  border: 1px solid var(--ta-border);
  border-radius: 12px;
  text-align: left;
  background: #fbfdff;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.prompt-card:hover {
  border-color: #bcd7fb;
  box-shadow: 0 10px 24px rgba(47, 126, 247, 0.1);
  transform: translateY(-1px);
}

.prompt-card span {
  color: var(--ta-ink);
  font-size: 12.5px;
  font-weight: 600;
}

.prompt-card small {
  overflow: hidden;
  max-width: 100%;
  color: var(--ta-ink-muted);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-card__arrow {
  position: absolute;
  top: 50%;
  right: 10px;
  color: #b4c7df;
  font-size: 13px;
  transform: translateY(-50%);
}

.composer {
  padding: 13px 22px 18px;
  border-top: 1px solid #eef5fd;
  background: rgba(255, 255, 255, 0.76);
}

.composer__box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 9px 9px 9px 14px;
  border: 1px solid var(--ta-border);
  border-radius: 16px;
  background: #fbfdff;
  transition: border-color 0.18s ease;
}

.composer__box:focus-within {
  border-color: #b9d6fa;
}

.composer__box :deep(.el-textarea__inner) {
  min-height: 46px !important;
  padding: 4px 2px;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.composer__box :deep(.el-textarea.is-disabled .el-textarea__inner) {
  color: var(--ta-ink-muted);
  background: transparent;
  cursor: not-allowed;
}

.composer__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}

.composer__send,
.composer__stop {
  width: 42px;
  height: 42px;
  font-size: 17px;
}

.composer__stop {
  background: #fff;
}

.stop-square {
  display: block;
  width: 11px;
  height: 11px;
  border-radius: 2px;
  background: var(--ta-primary);
}

.composer__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.composer__hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.composer__disclaimer {
  flex: 0 0 auto;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-height: 820px) {
  .chat-stream--conversation {
    max-height: 46vh;
  }
}

@media (max-width: 800px) {
  .chat-panel__head {
    padding: 14px 16px;
    flex-wrap: wrap;
  }

  .chat-panel__actions {
    width: 100%;
    justify-content: flex-end;
  }

  .chat-stream {
    padding: 18px 16px 12px;
  }

  .chat-stream--conversation {
    max-height: 58vh;
  }

  .prompt-grid {
    grid-template-columns: 1fr;
  }

  .welcome-message__avatar {
    display: none;
  }

  .welcome-message__bubble {
    padding: 16px;
  }

  .composer {
    padding: 11px 14px 14px;
  }

  .composer__meta {
    flex-wrap: wrap;
  }

  .composer__disclaimer {
    display: none;
  }
}
</style>
