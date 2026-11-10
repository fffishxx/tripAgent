import type { Component } from 'vue'
import {
  Calendar,
  ChatDotRound,
  Clock,
  Location,
  Tickets,
  User,
} from '@element-plus/icons-vue'

export interface NavigationItem {
  path: string
  name: string
  title: string
  icon: Component
  requiresAuth: boolean
}

export const navigationItems: NavigationItem[] = [
  {
    path: '/chat',
    name: 'chat-home',
    title: '新建对话',
    icon: ChatDotRound,
    requiresAuth: false,
  },
  {
    path: '/history',
    name: 'history',
    title: '历史会话',
    icon: Clock,
    requiresAuth: true,
  },
  {
    path: '/trips',
    name: 'trips',
    title: '我的行程',
    icon: Calendar,
    requiresAuth: true,
  },
  {
    path: '/map',
    name: 'map',
    title: '地图探索',
    icon: Location,
    requiresAuth: false,
  },
  {
    path: '/orders',
    name: 'orders',
    title: '我的订单',
    icon: Tickets,
    requiresAuth: true,
  },
  {
    path: '/profile',
    name: 'profile',
    title: '个人中心',
    icon: User,
    requiresAuth: true,
  },
]
