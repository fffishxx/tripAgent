<script setup lang="ts">
import AppSidebar from '@/layouts/components/AppSidebar.vue'
import AppTopbar from '@/layouts/components/AppTopbar.vue'
</script>

<template>
  <div class="app-shell">
    <AppSidebar />

    <div class="app-shell__main">
      <AppTopbar />

      <main class="app-shell__content">
        <RouterView v-slot="{ Component, route }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="route.path" />
          </Transition>
        </RouterView>
      </main>

      <footer class="app-shell__footer">
        TripAgent · AI 旅行规划 · 行程管理 · 地图探索 · 模拟打车
      </footer>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
}

.app-shell__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 100vh;
  margin-left: var(--ta-sidebar-width);
  transition: margin-left 0.2s ease;
}

.app-shell__content {
  flex: 1;
  width: 100%;
  max-width: 1420px;
  margin: 0 auto;
  padding: 28px 34px 26px;
}

.app-shell__footer {
  padding: 0 34px 22px;
  color: var(--ta-ink-muted);
  font-size: 11px;
  letter-spacing: 0.03em;
  text-align: center;
}

@media (max-width: 1100px) {
  .app-shell__content {
    padding: 24px 24px 22px;
  }

  .app-shell__footer {
    padding: 0 24px 18px;
  }
}

@media (max-width: 960px) {
  .app-shell__content {
    padding: 20px 20px 20px;
  }

  .app-shell__footer {
    padding: 0 20px 16px;
  }
}

@media (max-width: 720px) {
  .app-shell__main {
    margin-left: 0;
  }

  .app-shell__content {
    padding: 16px 14px 88px;
  }

  .app-shell__footer {
    display: none;
  }
}
</style>
