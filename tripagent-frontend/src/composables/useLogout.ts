import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

export function useLogout() {
  const router = useRouter()
  const auth = useAuthStore()

  async function logout(): Promise<void> {
    try {
      await ElMessageBox.confirm(
        '退出后仍可匿名与 AI 对话，确定退出登录吗？',
        '退出登录',
        {
          confirmButtonText: '退出',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )

      auth.logout()
      ElMessage.success('已退出登录')
      await router.replace({ name: 'chat-home' })
    } catch {
      // 用户取消退出，无需处理。
    }
  }

  return { logout }
}
