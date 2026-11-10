import http from '@/utils/request'
import type {
  AgentExample,
  AgentSessionDetailMessage,
  AgentSessionHistory,
  AgentSessionInfo,
} from '@/types/agent'

export const AGENT_ID = 1004

export interface AgentMutationResult {
  status?: string
  message?: string
  [key: string]: unknown
}

export function createAgentSession(n = 3): Promise<AgentSessionInfo> {
  return http.post<AgentSessionInfo>('/v1/agent/sessions', undefined, {
    params: {
      n,
      agent_id: AGENT_ID,
    },
  })
}

export function getHotQuestions(n = 3): Promise<AgentExample[]> {
  return http.get<AgentExample[]>('/v1/agent/sessions/hot', {
    params: {
      n,
      agent_id: AGENT_ID,
    },
  })
}

export function getSessionMessages(
  sessionId: string,
): Promise<AgentSessionDetailMessage[]> {
  return http.get<AgentSessionDetailMessage[]>(
    `/v1/agent/sessions/${AGENT_ID}/${sessionId}`,
  )
}

export function getSessionHistory(
  options: { silent?: boolean } = {},
): Promise<AgentSessionHistory> {
  return http.get<AgentSessionHistory>('/v1/agent/sessions/history', {
    params: {
      agent_id: AGENT_ID,
    },
    silent: options.silent,
  })
}

export function updateSessionTitle(
  sessionId: string,
  title: string,
): Promise<AgentMutationResult> {
  return http.put<AgentMutationResult>('/v1/agent/sessions/history', undefined, {
    params: {
      agent_id: AGENT_ID,
      session_id: sessionId,
      title,
    },
  })
}

export function deleteSessionHistory(
  sessionId: string,
): Promise<AgentMutationResult> {
  return http.delete<AgentMutationResult>('/v1/agent/sessions/history', {
    params: {
      agent_id: AGENT_ID,
      session_id: sessionId,
    },
  })
}
