<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, SwitchButton, User, UserFilled } from '@element-plus/icons-vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useLogout } from '@/composables/useLogout'
import { useAuthStore } from '@/stores/auth'
import { maskPhone } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { logout } = useLogout()

const currentTitle = computed(() => route.meta.title || 'TripAgent')
const maskedPhone = computed(() =>
  auth.user?.phone ? maskPhone(auth.user.phone) : '已登录',
)

function goToLogin(): void {
  void router.push({
    name: 'login',
    query: {
      redirect: route.fullPath,
    },
  })
}

function handleCommand(command: string | number | object): void {
  if (command === 'logout') {
    void logout()
    return
  }

  if (command === 'profile') {
    void router.push({ name: 'profile' })
  }
}
</script>

<template>
  <header class="topbar">
    <div class="topbar__context">
      <span class="topbar__product">TripAgent</span>
      <span class="topbar__slash">/</span>
      <span class="topbar__title">{{ currentTitle }}</span>
    </div>

    <div class="topbar__actions">
      <span class="env-pill">
        <span class="env-pill__dot" />
        本地环境
      </span>

      <el-dropdown
        v-if="auth.isLoggedIn"
        trigger="click"
        placement="bottom-end"
        @command="handleCommand"
      >
        <button class="user-chip" type="button">
          <UserAvatar
            :name="auth.displayName"
            :src="auth.user?.avatarUrl ?? null"
            :size="34"
          />
          <span class="user-chip__meta">
            <span class="user-chip__name">
              <strong>{{ auth.displayName }}</strong>
            </span>
            <small>{{ maskedPhone }}</small>
          </span>
          <el-icon class="user-chip__arrow"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile" :icon="User">
              个人中心
            </el-dropdown-item>
            <el-dropdown-item command="logout" :icon="SwitchButton" divided>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <template v-else>
        <span class="guest-info">
          <span class="guest-info__avatar">
            <el-icon><UserFilled /></el-icon>
          </span>
          <span class="guest-info__meta">
            <strong>游客模式</strong>
            <small>登录后可保存行程</small>
          </span>
        </span>
        <el-button type="primary" size="small" round @click="goToLogin">
          登录
        </el-button>
      </template>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  height: var(--ta-topbar-height);
  padding: 0 34px;
  border-bottom: 1px solid rgba(219, 233, 249, 0.68);
  background: rgba(249, 252, 255, 0.74);
  backdrop-filter: blur(18px);
}

.topbar__context {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--ta-ink-muted);
  font-size: 12px;
}

.topbar__product {
  font-weight: 600;
  letter-spacing: 0.02em;
}

.topbar__slash {
  color: #c2d2e6;
}

.topbar__title {
  overflow: hidden;
  color: var(--ta-ink-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar__actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.env-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid rgba(214, 231, 249, 0.9);
  border-radius: 999px;
  color: #7d8ea7;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.7);
}

.env-pill__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #ebaa3e;
  box-shadow: 0 0 0 3px rgba(235, 170, 62, 0.14);
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 5px 9px 5px 5px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease;
}

.user-chip:hover {
  border-color: var(--ta-border);
  background: rgba(255, 255, 255, 0.72);
}

.user-chip__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  line-height: 1.25;
}

.user-chip__name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.user-chip__name strong {
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
}

.user-chip__meta small {
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.user-chip__arrow {
  color: var(--ta-ink-muted);
  font-size: 12px;
}

.guest-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.guest-info__avatar {
  display: grid;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #7b91ad;
  font-size: 16px;
  place-items: center;
  background: #edf4fc;
}

.guest-info__meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.guest-info__meta strong {
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
}

.guest-info__meta small {
  color: var(--ta-ink-muted);
  font-size: 10px;
}

@media (max-width: 1100px) {
  .topbar {
    padding: 0 24px;
  }
}

@media (max-width: 960px) {
  .topbar {
    padding: 0 20px;
  }

  .env-pill {
    display: none;
  }
}

@media (max-width: 720px) {
  .topbar {
    height: 62px;
    padding: 0 16px;
  }

  .topbar__product,
  .topbar__slash,
  .guest-info__meta,
  .user-chip__meta {
    display: none;
  }

  .topbar__actions {
    gap: 8px;
  }

  .user-chip {
    padding: 3px;
  }

  .user-chip__arrow {
    display: none;
  }

  .guest-info {
    gap: 0;
  }
}
</style>
