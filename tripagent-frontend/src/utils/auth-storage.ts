import type { StoredAuthSession, UserSummary } from '@/types/auth'

const AUTH_STORAGE_KEY = 'tripagent:auth-session'

function isUserSummary(value: unknown): value is UserSummary {
  if (!value || typeof value !== 'object') {
    return false
  }

  const user = value as Partial<UserSummary>
  return (
    typeof user.userId === 'string' &&
    typeof user.phone === 'string' &&
    typeof user.nickname === 'string'
  )
}

export function readAuthSession(): StoredAuthSession | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) {
      return null
    }

    const parsed = JSON.parse(raw) as Partial<StoredAuthSession> & {
      mode?: unknown
    }
    const isExpired =
      typeof parsed.expiresAt === 'number' && parsed.expiresAt <= Date.now()
    const isLegacyMockSession =
      parsed.mode === 'mock' ||
      (typeof parsed.token === 'string' && parsed.token.startsWith('mock.'))

    if (!parsed.token || !isUserSummary(parsed.user) || isExpired || isLegacyMockSession) {
      try {
        localStorage.removeItem(AUTH_STORAGE_KEY)
      } catch {
        // 存储不可用时忽略清理失败。
      }
      return null
    }

    return {
      token: parsed.token,
      user: parsed.user,
      expiresAt: typeof parsed.expiresAt === 'number' ? parsed.expiresAt : null,
    }
  } catch {
    try {
      localStorage.removeItem(AUTH_STORAGE_KEY)
    } catch {
      // 存储不可用时忽略清理失败。
    }
    return null
  }
}

export function writeAuthSession(session: StoredAuthSession): void {
  try {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
  } catch {
    // 浏览器禁用存储或配额不足时，仍保留内存态登录。
  }
}

export function clearAuthSession(): void {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY)
  } catch {
    // 存储不可用时忽略清理失败。
  }
}
