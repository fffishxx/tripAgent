import type { AgentSseEvent } from '@/types/agent'
import { AGENT_ID } from '@/api/agent'

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')

export class AgentStreamError extends Error {
  readonly status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'AgentStreamError'
    this.status = status
  }
}

interface StreamAgentChatOptions {
  token?: string | null
  signal?: AbortSignal
  onEvent: (event: AgentSseEvent) => void
}

function extractErrorMessage(payload: unknown, fallback: string): string {
  if (typeof payload === 'string') {
    const text = payload.trim()
    return text && !text.startsWith('<') && text.length <= 300 ? text : fallback
  }

  if (payload && typeof payload === 'object') {
    const record = payload as Record<string, unknown>
    for (const key of ['message', 'detail', 'error', 'msg'] as const) {
      const value = record[key]
      if (typeof value === 'string' && value.trim()) {
        return value
      }
    }
  }

  return fallback
}

function getHttpFallbackMessage(status: number): string {
  const messages: Record<number, string> = {
    400: '对话参数有误',
    401: '登录状态已失效，请重新登录',
    403: '当前会话无权访问',
    404: '会话不存在或无权访问',
    409: '会话状态已变化，请重试',
    500: 'AI 服务暂时不可用，请稍后重试',
    503: 'AI 服务暂时不可用，请稍后重试',
  }
  return messages[status] || `对话请求失败（HTTP ${status}）`
}

async function readResponseError(response: Response): Promise<string> {
  const fallback = getHttpFallbackMessage(response.status)
  const text = await response.text().catch(() => '')
  if (!text) {
    return fallback
  }

  try {
    return extractErrorMessage(JSON.parse(text), fallback)
  } catch {
    return extractErrorMessage(text, fallback)
  }
}

function extractSseData(frame: string): string {
  return frame
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).replace(/^ /, ''))
    .join('\n')
}

function parseSseFrame(frame: string): AgentSseEvent | null {
  const data = extractSseData(frame)

  if (!data || data === '[DONE]') {
    return null
  }

  try {
    const parsed = JSON.parse(data) as AgentSseEvent
    if (!parsed || typeof parsed.eventType !== 'number') {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  )
}

function isTerminalEvent(event: AgentSseEvent): boolean {
  return (
    event.eventType === 1002 ||
    event.eventType === 2001 ||
    event.eventType === 2002
  )
}

/**
 * 使用 Fetch + ReadableStream 读取 Java Agent 代理返回的原生 SSE。
 * 每条 SSE 事件形如：data: {"eventType":1001,"eventData":"..."}
 */
export async function streamAgentChat(
  question: string,
  sessionId: string,
  options: StreamAgentChatOptions,
): Promise<void> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json; charset=utf-8',
    Accept: 'text/event-stream',
  }
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE}/v1/agent/chat`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        question,
        sessionId,
        agentId: AGENT_ID,
      }),
      signal: options.signal,
    })
  } catch (error) {
    if (isAbortError(error) || options.signal?.aborted) {
      throw error
    }
    throw new AgentStreamError(
      '网络连接失败，AI 服务暂时不可用，请检查网络后重试',
    )
  }

  if (!response.ok) {
    throw new AgentStreamError(await readResponseError(response), response.status)
  }

  const contentType = (response.headers.get('content-type') || '').toLowerCase()
  if (!contentType.includes('text/event-stream')) {
    const text = await response.text().catch(() => '')
    let message = 'AI 服务返回了无法识别的响应，请稍后重试'
    let status = response.status
    try {
      const payload = JSON.parse(text) as Record<string, unknown>
      if (payload && typeof payload === 'object') {
        message = extractErrorMessage(payload, message)
        const code = String(payload.code ?? '')
        if (code === '40101') {
          status = 401
        } else if (code === '40301' || code === '40302') {
          status = 403
        }
      }
    } catch {
      message = extractErrorMessage(text, message)
    }
    throw new AgentStreamError(message, status)
  }

  if (!response.body) {
    throw new AgentStreamError('当前浏览器不支持流式响应，请升级浏览器后重试')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finished = false

  const dispatchFrame = (frame: string): void => {
    if (extractSseData(frame) === '[DONE]') {
      finished = true
      return
    }

    const event = parseSseFrame(frame)
    if (!event) {
      return
    }
    options.onEvent(event)
    if (isTerminalEvent(event)) {
      finished = true
    }
  }

  try {
    while (!finished) {
      let chunk: ReadableStreamReadResult<Uint8Array>
      try {
        chunk = await reader.read()
      } catch (error) {
        if (isAbortError(error) || options.signal?.aborted) {
          throw error
        }
        throw new AgentStreamError(
          '回复生成中断，网络连接可能已断开，请重试',
        )
      }

      if (chunk.done) {
        break
      }

      buffer += decoder.decode(chunk.value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n')

      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1 && !finished) {
        const frame = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        dispatchFrame(frame)
        boundary = buffer.indexOf('\n\n')
      }
    }

    if (!finished) {
      buffer += decoder.decode()
      const trailing = buffer.trim()
      if (trailing) {
        dispatchFrame(trailing)
      }
    }

    if (!finished) {
      throw new AgentStreamError(
        '回复生成中断，未收到结束标记，请重试',
      )
    }
  } finally {
    reader.cancel().catch(() => undefined)
  }
}

/** 停止服务端生成；属于尽力而为操作，不阻塞前端停止状态。 */
export async function requestStopAgentSession(
  sessionId: string,
  token?: string | null,
): Promise<void> {
  const params = new URLSearchParams({
    session_id: sessionId,
    agent_id: String(AGENT_ID),
  })
  const headers: Record<string, string> = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  try {
    await fetch(`${API_BASE}/v1/agent/sessions/stop?${params.toString()}`, {
      method: 'POST',
      headers,
    })
  } catch {
    // 停止接口失败不影响前端已经中止的流式读取。
  }
}
