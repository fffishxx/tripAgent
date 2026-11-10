import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@/styles/index.scss'
import '@/styles/auth.scss'
import '@/styles/map.scss'
import App from './App.vue'
import router from './router'
import { pinia } from './stores'

const app = createApp(App)

let lastGlobalErrorAt = 0

function reportGlobalError(context: string, error: unknown): void {
  console.error(`[${context}]`, error)

  const now = Date.now()
  if (now - lastGlobalErrorAt < 3000) {
    return
  }
  lastGlobalErrorAt = now
  ElMessage.error('页面出现异常，请刷新或重试')
}

app.config.errorHandler = (error, _instance, info) => {
  reportGlobalError(`Vue ${info}`, error)
}

router.onError((error) => {
  reportGlobalError('router', error)
})

window.addEventListener('unhandledrejection', (event) => {
  reportGlobalError('unhandledrejection', event.reason)
})

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
