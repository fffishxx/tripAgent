/**
 * Java 后端统一响应结构。
 * Agent 代理的会话/历史接口不套用此结构，而是原样透传 Python JSON。
 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  requestId?: string
  timestamp?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}
