import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: {
        title: '登录',
      },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/auth/RegisterView.vue'),
      meta: {
        title: '注册',
      },
    },
    {
      path: '/',
      component: MainLayout,
      redirect: { name: 'chat-home' },
      children: [
        {
          path: 'chat/:sessionId?',
          name: 'chat-home',
          component: () => import('@/views/chat/ChatHomeView.vue'),
          meta: {
            title: '新建对话',
          },
        },
        {
          path: 'history',
          name: 'history',
          component: () => import('@/views/chat/HistoryView.vue'),
          meta: {
            title: '历史会话',
            requiresAuth: true,
          },
        },
        {
          path: 'trips',
          name: 'trips',
          component: () => import('@/views/trips/TripListView.vue'),
          meta: {
            title: '我的行程',
            requiresAuth: true,
          },
        },
        {
          path: 'trips/new',
          name: 'trip-create',
          component: () => import('@/views/trips/TripCreateView.vue'),
          meta: {
            title: '创建行程',
            requiresAuth: true,
          },
        },
        {
          path: 'trips/:tripId',
          name: 'trip-detail',
          component: () => import('@/views/trips/TripDetailView.vue'),
          meta: {
            title: '行程详情',
            requiresAuth: true,
          },
        },
        {
          path: 'map',
          name: 'map',
          component: () => import('@/views/map/MapExploreView.vue'),
          meta: {
            title: '地图探索',
          },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/orders/OrderListView.vue'),
          meta: {
            title: '我的订单',
            requiresAuth: true,
          },
        },
        {
          path: 'orders/new',
          name: 'order-create',
          component: () => import('@/views/orders/OrderCreateView.vue'),
          meta: {
            title: '模拟打车',
            requiresAuth: true,
          },
        },
        {
          path: 'orders/:orderId',
          name: 'order-detail',
          component: () => import('@/views/orders/OrderDetailView.vue'),
          meta: {
            title: '订单详情',
            requiresAuth: true,
          },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/profile/ProfileView.vue'),
          meta: {
            title: '个人中心',
            requiresAuth: true,
          },
        },
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: () => import('@/views/NotFoundView.vue'),
          meta: {
            title: '页面不存在',
          },
        },
      ],
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const auth = useAuthStore(pinia)
  auth.ensureSessionActive()

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  // 历史会话详情必须登录后查看；普通聊天仍允许匿名。
  if (to.name === 'chat-home' && to.params.sessionId && !auth.isLoggedIn) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if ((to.name === 'login' || to.name === 'register') && auth.isLoggedIn) {
    return { name: 'chat-home' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} · TripAgent`
    : 'TripAgent · 你的智能旅行助手'
})

export default router
