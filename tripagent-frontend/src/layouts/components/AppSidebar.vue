<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import BrandLogo from '@/components/BrandLogo.vue'
import SceneryPlaceholder from '@/components/SceneryPlaceholder.vue'
import { navigationItems } from '@/router/navigation'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

function isItemActive(path: string): boolean {
  if (path === '/chat') {
    return route.path === '/chat' || route.path.startsWith('/chat/')
  }
  return route.path === path || route.path.startsWith(`${path}/`)
}

function handleNavigation(path: string, event: MouseEvent): void {
  // 当前地址可能已被聊天页 replaceState 为 /chat/:id，但路由仍处于 /chat；
  // 此时直接跳到 /chat 会被视为重复导航，用一次性 query 通知聊天页开始新会话。
  if (path === '/chat' && route.path === '/chat') {
    event.preventDefault()
    void router.replace({
      name: 'chat-home',
      query: { newChat: Date.now().toString(36) },
    })
  }
}
</script>

<template>
  <aside class="sidebar">
    <BrandLogo class="sidebar__brand" />

    <nav class="sidebar__nav" aria-label="主导航">
      <RouterLink
        v-for="item in navigationItems"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        :class="{ 'nav-item--active': isItemActive(item.path) }"
        :aria-label="item.title"
        :title="item.title"
        @click="handleNavigation(item.path, $event)"
      >
        <span class="nav-item__icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <span class="nav-item__label">{{ item.title }}</span>
        <el-icon
          v-if="item.requiresAuth && !auth.isLoggedIn"
          class="nav-item__lock"
          aria-label="需要登录"
        >
          <Lock />
        </el-icon>
      </RouterLink>
    </nav>

    <div class="sidebar__footer">
      <SceneryPlaceholder
        class="sidebar__scenery"
        variant="sidebar"
        label="山水插画占位"
      />
      <div class="sidebar__progress">
        <span>开发进度</span>
        <strong>阶段 6 / 7</strong>
      </div>
      <div class="sidebar__progress-bar" aria-hidden="true">
        <span class="sidebar__progress-fill" />
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  width: var(--ta-sidebar-width);
  padding: 26px 18px 20px;
  border-right: 1px solid rgba(214, 231, 249, 0.88);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(20px);
  transition:
    width 0.2s ease,
    padding 0.2s ease;
}

.sidebar__brand {
  margin: 2px 8px 30px;
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  min-height: 0;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 46px;
  padding: 0 13px;
  border-radius: 13px;
  color: var(--ta-ink-secondary);
  font-size: 14px;
  font-weight: 500;
  transition:
    color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.nav-item__icon {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  font-size: 18px;
  place-items: center;
  transition:
    color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.nav-item__label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
}

.nav-item__lock {
  color: var(--ta-ink-muted);
  font-size: 13px;
  opacity: 0.7;
}

.nav-item:not(.nav-item--active):hover {
  color: var(--ta-primary);
  background: #f4f9ff;
}

.nav-item:not(.nav-item--active):hover .nav-item__icon {
  background: #e9f3ff;
}

.nav-item--active {
  color: var(--ta-primary-deep);
  background: linear-gradient(135deg, #eff6ff, #e4efff);
  box-shadow: inset 0 0 0 1px rgba(47, 126, 247, 0.08);
}

.nav-item--active .nav-item__icon {
  color: #fff;
  background: linear-gradient(135deg, #559bff, #2d76ee);
  box-shadow: 0 6px 14px rgba(47, 126, 247, 0.24);
}

.sidebar__footer {
  margin-top: auto;
  padding-top: 18px;
}

.sidebar__scenery {
  margin-bottom: 14px;
}

.sidebar__progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px;
  color: var(--ta-ink-muted);
  font-size: 11px;
}

.sidebar__progress strong {
  color: var(--ta-primary);
  font-weight: 600;
}

.sidebar__progress-bar {
  overflow: hidden;
  height: 4px;
  margin-top: 7px;
  border-radius: 999px;
  background: #e8f1fc;
}

.sidebar__progress-fill {
  display: block;
  width: 85.71%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #6aabff, #2f7ef7);
}

@media (max-height: 760px) {
  .sidebar__scenery {
    display: none;
  }
}

@media (max-width: 960px) {
  .sidebar {
    align-items: center;
    padding: 22px 12px 18px;
  }

  .sidebar__brand {
    margin: 0 0 26px;
  }

  .sidebar__brand :deep(.brand__text) {
    display: none;
  }

  .sidebar__nav {
    width: 100%;
  }

  .nav-item {
    justify-content: center;
    padding: 0;
  }

  .nav-item__label,
  .nav-item__lock,
  .sidebar__footer {
    display: none;
  }
}

@media (max-width: 720px) {
  .sidebar {
    top: auto;
    right: 0;
    bottom: 0;
    flex-direction: row;
    align-items: center;
    width: 100%;
    height: 68px;
    padding: 6px 8px;
    border-top: 1px solid rgba(214, 231, 249, 0.92);
    border-right: 0;
    background: rgba(255, 255, 255, 0.92);
  }

  .sidebar__brand {
    display: none;
  }

  .sidebar__nav {
    flex-direction: row;
    flex: 1;
    justify-content: space-around;
    gap: 2px;
    overflow: visible;
  }

  .nav-item {
    flex: 1;
    flex-direction: column;
    justify-content: center;
    gap: 2px;
    min-height: auto;
    padding: 4px 2px;
    border-radius: 10px;
  }

  .nav-item__icon {
    width: 24px;
    height: 24px;
    font-size: 19px;
    background: transparent;
    box-shadow: none;
  }

  .nav-item__label {
    display: block;
    font-size: 10px;
    line-height: 1.2;
  }

  .nav-item--active .nav-item__icon {
    color: var(--ta-primary);
    background: transparent;
    box-shadow: none;
  }
}
</style>
