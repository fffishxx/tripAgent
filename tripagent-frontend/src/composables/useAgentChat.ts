import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { createAgentSession, getHotQuestions, getSessionMessages } from '@/api/agent'
import {
  AgentStreamError,
  requestStopAgentSession,
  streamAgentChat,
} from '@/api/agent-stream'
import { useAuthStore } from '@/stores/auth'
import { handleUnauthorized } from '@/utils/request'
import type {
  AgentAuthRequired,
  AgentExample,
  AgentSessionDetailMessage,
  AgentSseEvent,
  AgentTaxiCard,
  AgentTripPlan,
  ChatMessage,
  SystemMessageVariant,
} from '@/types/agent'
import { createId } from '@/utils/format'

const DEFAULT_WELCOME_TITLE = '你好，我是 TripAgent'
const DEFAULT_WELCOME_TEXT =
  '我可以为你规划旅行行程、查询天气与景点、推荐当地美食，并在后台校验行程路线是否合理。'

const FALLBACK_EXAMPLES: AgentExample[] = [
  { title: '行程规划', describe: '帮我规划杭州三日游' },
  { title: '路线推荐', describe: '从西湖到灵隐寺怎么走？' },
  { title: '旅行问答', describe: '杭州有哪些必吃的本地美食？' },
]

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '请求失败，请稍后重试'
}

function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  )
}

function mapSessionMessage(raw: AgentSessionDetailMessage): ChatMessage | null {
  const role =
    raw?.type === 'USER' ? 'user' : raw?.type === 'ASSISTANT' ? 'assistant' : null
  if (!role) {
    return null
  }

  const params = raw.params ?? {}
  const tripPlan = (params.tripPlan ?? params.trip_plan) as
    | AgentTripPlan
    | undefined
  const taxiOrder = (params.taxiOrder ?? params.taxi_order) as
    | AgentTaxiCard
    | undefined

  return {
    id: createId('msg-'),
    role,
    content: typeof raw.content === 'string' ? raw.content : '',
    tripPlan: tripPlan ?? null,
    taxiOrder: taxiOrder ?? null,
    createdAt: Date.now(),
  }
}

export function useAgentChat() {
  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()

  const initialSessionId =
    typeof route.params.sessionId === 'string' ? route.params.sessionId : null

  const messages = ref<ChatMessage[]>([])
  const isInitializing = ref(false)
  const isStreaming = ref(false)
  const sessionId = ref<string | null>(null)
  const examples = ref<AgentExample[]>([])
  const welcomeTitle = ref(DEFAULT_WELCOME_TITLE)
  const welcomeText = ref(DEFAULT_WELCOME_TEXT)
  const loadError = ref('')
  const scrollTick = ref(0)

  const hasMessages = computed(() => messages.value.length > 0)
  const suggestedExamples = computed(() =>
    examples.value.length > 0 ? examples.value : FALLBACK_EXAMPLES,
  )

  let controller: AbortController | null = null
  let stopRequested = false
  let streamGeneration = 0
  let disposed = false
  let idleTimer: ReturnType<typeof setTimeout> | null = null
  let idleTimedOut = false

  const STREAM_IDLE_TIMEOUT_MS = 90_000

  function clearIdleTimer(): void {
    if (idleTimer !== null) {
      clearTimeout(idleTimer)
      idleTimer = null
    }
  }

  function resetIdleTimer(): void {
    clearIdleTimer()
    idleTimer = setTimeout(() => {
      idleTimedOut = true
      controller?.abort()
    }, STREAM_IDLE_TIMEOUT_MS)
  }

  function bumpScroll(): void {
    if (!disposed) {
      scrollTick.value += 1
    }
  }

  function findMessage(messageId: string): ChatMessage | undefined {
    return messages.value.find((message) => message.id === messageId)
  }

  function addSystemMessage(
    content: string,
    variant: SystemMessageVariant,
    loginRequired?: AgentAuthRequired,
  ): void {
    messages.value.push({
      id: createId('sys-'),
      role: 'system',
      content,
      variant,
      loginRequired: loginRequired ?? null,
      createdAt: Date.now(),
    })
  }

  async function loadSession(id: string): Promise<boolean> {
    if (isStreaming.value && sessionId.value) {
      void requestStopAgentSession(sessionId.value, auth.token)
    }
    streamGeneration += 1
    controller?.abort()
    controller = null
    isStreaming.value = false

    isInitializing.value = true
    loadError.value = ''
    messages.value = []
    examples.value = []
    sessionId.value = id
    bumpScroll()

    let succeeded = false
    try {
      const data = await getSessionMessages(id)
      messages.value = (Array.isArray(data) ? data : [])
        .map(mapSessionMessage)
        .filter((message): message is ChatMessage => message !== null)
      succeeded = true
    } catch (error) {
      loadError.value = getErrorMessage(error)
      sessionId.value = null
    } finally {
      isInitializing.value = false
      bumpScroll()
    }
    return succeeded
  }

  async function startNewChat(): Promise<void> {
    if (isStreaming.value && sessionId.value) {
      void requestStopAgentSession(sessionId.value, auth.token)
    }
    streamGeneration += 1
    controller?.abort()
    controller = null
    isStreaming.value = false
    stopRequested = false

    messages.value = []
    examples.value = []
    loadError.value = ''
    sessionId.value = null
    welcomeTitle.value = DEFAULT_WELCOME_TITLE
    welcomeText.value = DEFAULT_WELCOME_TEXT
    isInitializing.value = true
    bumpScroll()

    try {
      if (auth.isLoggedIn) {
        const info = await createAgentSession(3)
        sessionId.value = info.sessionId
        welcomeTitle.value = info.title || DEFAULT_WELCOME_TITLE
        welcomeText.value = info.describe || DEFAULT_WELCOME_TEXT
        examples.value = Array.isArray(info.examples) ? info.examples : []
        // 仅同步浏览器地址，不触发路由重挂载；刷新后可恢复当前会话。
        try {
          const href = router.resolve({
            name: 'chat-home',
            params: { sessionId: info.sessionId },
          }).href
          window.history.replaceState(window.history.state, '', href)
        } catch {
          // 地址同步失败不影响当前聊天。
        }
      } else {
        // 匿名会话不创建 chat_session，由前端生成 32 位 UUID。
        sessionId.value = createId()
        try {
          const hot = await getHotQuestions(3)
          examples.value = Array.isArray(hot) ? hot : []
        } catch {
          examples.value = []
        }
      }
    } catch (error) {
      loadError.value = getErrorMessage(error)
    } finally {
      isInitializing.value = false
      bumpScroll()
    }
  }

  async function bootstrap(): Promise<void> {
    if (initialSessionId) {
      await loadSession(initialSessionId)
      return
    }
    await startNewChat()
  }

  async function retryLoad(): Promise<boolean> {
    if (initialSessionId) {
      return loadSession(initialSessionId)
    }
    await startNewChat()
    return !loadError.value
  }

  function handleEvent(
    messageId: string,
    event: AgentSseEvent,
    generation: number,
  ): void {
    if (generation !== streamGeneration || disposed) {
      return
    }

    const message = findMessage(messageId)
    if (!message) {
      return
    }

    switch (event.eventType) {
      case 1001:
        if (typeof event.eventData === 'string') {
          message.content += event.eventData
        }
        break
      case 1005:
        message.tripPlan = event.eventData
        break
      case 1006:
        message.validation = event.eventData
        break
      case 1007:
        message.taxiOrder = event.eventData
        break
      case 2001:
        addSystemMessage(
          typeof event.eventData === 'string' && event.eventData.trim()
            ? event.eventData
            : '本轮对话出现异常，请稍后重试',
          'error',
        )
        message.streaming = false
        break
      case 2002: {
        const loginRequired: AgentAuthRequired =
          event.eventData && typeof event.eventData === 'object'
            ? event.eventData
            : {
                reason: 'LOGIN_REQUIRED',
                message: '该操作需要先登录，请登录后再试。',
              }
        addSystemMessage(
          loginRequired.message || '该操作需要先登录，请登录后再试。',
          'login',
          loginRequired,
        )
        message.streaming = false
        break
      }
      case 1002:
      default:
        break
    }

    bumpScroll()
  }

  function handleStreamFailure(error: unknown): void {
    if (error instanceof AgentStreamError && error.status === 401) {
      void handleUnauthorized()
      return
    }

    addSystemMessage(getErrorMessage(error), 'error')
  }

  async function sendMessage(question: string): Promise<boolean> {
    const trimmed = question.trim()
    if (!trimmed || isStreaming.value || isInitializing.value) {
      return false
    }

    const currentSessionId = sessionId.value
    if (!currentSessionId) {
      ElMessage.warning('会话尚未准备好，请重新开始一个对话')
      return false
    }

    const generation = ++streamGeneration
    stopRequested = false
    idleTimedOut = false
    let serverError = false

    messages.value.push({
      id: createId('msg-'),
      role: 'user',
      content: trimmed,
      createdAt: Date.now(),
    })

    const assistant: ChatMessage = {
      id: createId('msg-'),
      role: 'assistant',
      content: '',
      streaming: true,
      createdAt: Date.now(),
    }
    messages.value.push(assistant)

    isStreaming.value = true
    controller = new AbortController()
    resetIdleTimer()
    bumpScroll()

    try {
      await streamAgentChat(trimmed, currentSessionId, {
        token: auth.token,
        signal: controller.signal,
        onEvent: (event) => {
          resetIdleTimer()
          if (event.eventType === 2001) {
            serverError = true
          }
          handleEvent(assistant.id, event, generation)
        },
      })
      return !serverError
    } catch (error) {
      if (generation !== streamGeneration || disposed) {
        return false
      }

      if (isAbortError(error) && idleTimedOut) {
        addSystemMessage('回复生成超时，请稍后重试', 'error')
        return false
      }

      if (isAbortError(error) && stopRequested) {
        addSystemMessage('已停止生成', 'info')
        return true
      }

      handleStreamFailure(error)
      return false
    } finally {
      clearIdleTimer()
      if (generation === streamGeneration && !disposed) {
        const current = findMessage(assistant.id)
        if (current) {
          current.streaming = false
          if (!current.content && !current.tripPlan && !current.taxiOrder) {
            const index = messages.value.findIndex(
              (message) => message.id === assistant.id,
            )
            if (index !== -1) {
              messages.value.splice(index, 1)
            }
          }
        }
        isStreaming.value = false
        controller = null
        bumpScroll()
      }
    }
  }

  function stopGeneration(): void {
    if (!isStreaming.value || stopRequested) {
      return
    }

    stopRequested = true
    clearIdleTimer()
    controller?.abort()
    if (sessionId.value) {
      void requestStopAgentSession(sessionId.value, auth.token)
    }
  }

  onBeforeUnmount(() => {
    disposed = true
    streamGeneration += 1
    if (isStreaming.value) {
      controller?.abort()
      if (sessionId.value) {
        void requestStopAgentSession(sessionId.value, auth.token)
      }
    }
    controller = null
  })

  return {
    messages,
    isInitializing,
    isStreaming,
    sessionId,
    examples,
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
  }
}
