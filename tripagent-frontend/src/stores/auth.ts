import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserSummary } from '@/types/auth'
import {
  clearAuthSession,
  readAuthSession,
  writeAuthSession,
} from '@/utils/auth-storage'
import { getAvatarText } from '@/utils/format'

interface SetSessionOptions {
  expiresInSeconds?: number | null
}

export const useAuthStore = defineStore('auth', () => {
  const cached = readAuthSession()

  const token = ref(cached?.token ?? '')
  const user = ref<UserSummary | null>(cached?.user ?? null)
  const expiresAt = ref<number | null>(cached?.expiresAt ?? null)

  const isLoggedIn = computed(() => {
    const hasToken = token.value.length > 0
    const notExpired = expiresAt.value === null || expiresAt.value > Date.now()
    return hasToken && notExpired
  })

  const displayName = computed(() => user.value?.nickname || '游客')
  const avatarText = computed(() => getAvatarText(displayName.value))

  function setSession(
    accessToken: string,
    profile: UserSummary,
    options: SetSessionOptions = {},
  ): void {
    const nextExpiresAt = options.expiresInSeconds
      ? Date.now() + options.expiresInSeconds * 1000
      : null

    token.value = accessToken
    user.value = profile
    expiresAt.value = nextExpiresAt

    writeAuthSession({
      token: accessToken,
      user: profile,
      expiresAt: nextExpiresAt,
    })
  }

  function setUser(profile: UserSummary): void {
    user.value = profile
    if (token.value) {
      writeAuthSession({
        token: token.value,
        user: profile,
        expiresAt: expiresAt.value,
      })
    }
  }

  function clearSession(): void {
    token.value = ''
    user.value = null
    expiresAt.value = null
    clearAuthSession()
  }

  function ensureSessionActive(): boolean {
    if (token.value && expiresAt.value !== null && expiresAt.value <= Date.now()) {
      clearSession()
    }
    return isLoggedIn.value
  }

  function logout(): void {
    clearSession()
  }

  return {
    token,
    user,
    expiresAt,
    isLoggedIn,
    displayName,
    avatarText,
    setSession,
    setUser,
    clearSession,
    ensureSessionActive,
    logout,
  }
})
