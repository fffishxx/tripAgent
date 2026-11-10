import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { handleUnauthorized } from '@/utils/request'

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')

export interface AvatarUploadResult {
  url: string
  objectKey: string
}

interface ApiEnvelope<T> {
  code?: number
  message?: string
  data?: T
  detail?: string
}

function extractErrorMessage(payload: unknown, fallback: string): string {
  if (typeof payload === 'string' && payload.trim() && !payload.startsWith('<')) {
    return payload.trim().slice(0, 300)
  }
  if (payload && typeof payload === 'object') {
    const record = payload as Record<string, unknown>
    for (const key of ['message', 'detail', 'error'] as const) {
      const value = record[key]
      if (typeof value === 'string' && value.trim()) {
        return value
      }
    }
  }
  return fallback
}

/**
 * 头像上传走 Java 后端代理，AccessKey 只存在服务端；
 * 浏览器只发送文件与用户 JWT。
 */
export async function uploadAvatar(file: File): Promise<AvatarUploadResult> {
  const auth = useAuthStore(pinia)
  const formData = new FormData()
  formData.append('file', file)

  const headers: Record<string, string> = {}
  if (auth.token) {
    headers.Authorization = `Bearer ${auth.token}`
  }

  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), 15_000)

  try {
    let response: Response
    try {
      response = await fetch(`${API_BASE}/v1/uploads/avatar`, {
        method: 'POST',
        headers,
        body: formData,
        signal: controller.signal,
      })
    } catch (error) {
      if (
        (error instanceof DOMException && error.name === 'AbortError') ||
        (error instanceof Error && error.name === 'AbortError')
      ) {
        throw new Error('头像上传超时，请检查网络后重试')
      }
      throw new Error('头像上传失败，请检查网络或后端服务')
    }

    let text = ''
    try {
      text = await response.text()
    } catch (error) {
      if (
        (error instanceof DOMException && error.name === 'AbortError') ||
        (error instanceof Error && error.name === 'AbortError')
      ) {
        throw new Error('头像上传超时，请检查网络后重试')
      }
    }

    let payload: ApiEnvelope<AvatarUploadResult> | null = null
    try {
      payload = text ? (JSON.parse(text) as ApiEnvelope<AvatarUploadResult>) : null
    } catch {
      payload = null
    }

    if (response.status === 401 || payload?.code === 40101) {
      await handleUnauthorized()
      throw new Error('登录状态已失效，请重新登录')
    }

    if (!response.ok || payload?.code !== 0 || !payload.data?.url) {
      throw new Error(
        extractErrorMessage(payload ?? text, `头像上传失败（HTTP ${response.status}）`),
      )
    }

    return payload.data
  } finally {
    window.clearTimeout(timer)
  }
}
