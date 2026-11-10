import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import type { ApiResult } from '@/types/api'

type BusinessCode = number | string | undefined

export interface RequestOptions extends AxiosRequestConfig {
  /** 由调用方自行处理错误提示时使用 */
  silent?: boolean
}

export class ApiError extends Error {
  readonly code: BusinessCode
  readonly status?: number
  readonly requestId?: string
  readonly data?: unknown

  constructor(
    message: string,
    options: {
      code?: BusinessCode
      status?: number
      requestId?: string
      data?: unknown
    } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = options.code
    this.status = options.status
    this.requestId = options.requestId
    this.data = options.data
  }
}

const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

httpClient.interceptors.request.use((config) => {
  const auth = useAuthStore(pinia)
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

let lastUnauthorizedAt = 0

export async function handleUnauthorized(): Promise<void> {
  const auth = useAuthStore(pinia)
  auth.clearSession()

  const now = Date.now()
  if (now - lastUnauthorizedAt < 1_500) {
    return
  }
  lastUnauthorizedAt = now

  const currentRoute = router.currentRoute.value
  if (currentRoute.name === 'login' || currentRoute.name === 'register') {
    return
  }

  ElMessage.error('登录状态已失效，请重新登录')
  await router.replace({
    name: 'login',
    query: currentRoute.fullPath ? { redirect: currentRoute.fullPath } : undefined,
  })
}

function isApiResult(payload: unknown): payload is ApiResult<unknown> {
  return (
    !!payload &&
    typeof payload === 'object' &&
    'code' in payload &&
    typeof (payload as { code?: unknown }).code === 'number'
  )
}

function extractErrorMessage(payload: unknown, fallback: string): string {
  if (typeof payload === 'string') {
    const text = payload.trim()
    return text && !text.startsWith('<') && text.length <= 200 ? text : fallback
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

function getBusinessCode(payload: unknown): BusinessCode {
  if (payload && typeof payload === 'object' && 'code' in payload) {
    const code = (payload as { code?: unknown }).code
    if (typeof code === 'number' || typeof code === 'string') {
      return code
    }
  }
  return undefined
}

function getRequestId(payload: unknown): string | undefined {
  if (payload && typeof payload === 'object' && 'requestId' in payload) {
    const requestId = (payload as { requestId?: unknown }).requestId
    return typeof requestId === 'string' ? requestId : undefined
  }
  return undefined
}

function getHttpFallbackMessage(status: number | undefined, timedOut: boolean): string {
  if (!status) {
    return timedOut ? '请求超时，请稍后重试' : '网络连接失败，请检查后端服务是否已启动'
  }

  const messages: Record<number, string> = {
    400: '请求参数有误',
    401: '未登录或登录已过期',
    403: '没有权限执行该操作',
    404: '请求的资源不存在',
    409: '数据已发生变化，请刷新后重试',
    500: '服务暂时不可用，请稍后重试',
    503: '服务暂时不可用，请稍后重试',
  }

  return messages[status] || `请求失败（HTTP ${status}）`
}

httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (!axios.isAxiosError(error)) {
      const message = error instanceof Error ? error.message : '请求失败，请稍后重试'
      ElMessage.error(message)
      return Promise.reject(new ApiError(message))
    }

    const status = error.response?.status
    const payload: unknown = error.response?.data
    const timedOut = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
    const message = extractErrorMessage(payload, getHttpFallbackMessage(status, timedOut))
    const silent = (error.config as RequestOptions | undefined)?.silent === true
    const apiError = new ApiError(message, {
      code: getBusinessCode(payload),
      status,
      requestId: getRequestId(payload),
      data:
        payload && typeof payload === 'object'
          ? (payload as { data?: unknown }).data
          : undefined,
    })

    if (status === 401) {
      void handleUnauthorized()
    } else if (!silent) {
      ElMessage.error(message)
    }

    return Promise.reject(apiError)
  },
)

function unwrapResponse<T>(
  payload: unknown,
  status?: number,
  silent = false,
): T {
  if (isApiResult(payload)) {
    if (payload.code === 0) {
      return payload.data as T
    }

    const error = new ApiError(payload.message || '请求失败', {
      code: payload.code,
      status,
      requestId: payload.requestId,
      data: payload.data,
    })

    if (payload.code === 40101) {
      void handleUnauthorized()
    } else if (!silent) {
      ElMessage.error(error.message)
    }

    throw error
  }

  // Agent 会话/历史接口原样透传 Python JSON，不套 Result。
  return payload as T
}

async function request<T>(config: RequestOptions): Promise<T> {
  const response = await httpClient.request<unknown>(config)
  return unwrapResponse<T>(response.data, response.status, config.silent)
}

interface HttpRequest {
  get<T>(url: string, config?: RequestOptions): Promise<T>
  post<T>(url: string, data?: unknown, config?: RequestOptions): Promise<T>
  put<T>(url: string, data?: unknown, config?: RequestOptions): Promise<T>
  delete<T>(url: string, config?: RequestOptions): Promise<T>
}

const http: HttpRequest = {
  get<T>(url: string, config?: RequestOptions): Promise<T> {
    return request<T>({ ...config, url, method: 'GET' })
  },
  post<T>(url: string, data?: unknown, config?: RequestOptions): Promise<T> {
    return request<T>({ ...config, url, method: 'POST', data })
  },
  put<T>(url: string, data?: unknown, config?: RequestOptions): Promise<T> {
    return request<T>({ ...config, url, method: 'PUT', data })
  },
  delete<T>(url: string, config?: RequestOptions): Promise<T> {
    return request<T>({ ...config, url, method: 'DELETE' })
  },
}

export { httpClient }
export default http
