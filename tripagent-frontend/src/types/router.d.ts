import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，同时用于浏览器标签页标题 */
    title?: string
    /** 是否要求登录 */
    requiresAuth?: boolean
  }
}
