<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Iphone, Lock } from '@element-plus/icons-vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  phone: typeof route.query.phone === 'string' ? route.query.phone : '',
  password: '',
})

const rules: FormRules<typeof form> = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入有效的中国大陆手机号',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      min: 8,
      max: 64,
      message: '密码长度需为 8-64 位',
      trigger: 'blur',
    },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
      message: '密码需同时包含字母和数字',
      trigger: 'blur',
    },
  ],
}

const registerLink = computed(() => {
  const query: Record<string, string> = {}
  if (typeof route.query.redirect === 'string') {
    query.redirect = route.query.redirect
  }
  if (form.phone) {
    query.phone = form.phone
  }
  return { name: 'register', query }
})

function resolveRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return '/chat'
  }
  return value
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value || submitting.value) {
    return
  }

  let valid = false
  try {
    valid = await formRef.value.validate()
  } catch {
    valid = false
  }

  if (!valid) {
    return
  }

  submitting.value = true
  try {
    const result = await login({
      phone: form.phone,
      password: form.password,
    })

    auth.setSession(result.accessToken, result.user, {
      expiresInSeconds: result.expiresIn,
    })

    ElMessage.success(`欢迎回来，${result.user.nickname}`)
    await router.replace(resolveRedirect(route.query.redirect))
  } catch (error) {
    // 登录接口的 401 由登录页自行提示；其他错误已由请求层统提示。
    if (
      error instanceof ApiError &&
      (error.status === 401 || error.code === 40101)
    ) {
      ElMessage.error(error.message || '手机号或密码错误')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <h2>欢迎回来</h2>
    <p class="auth-card__desc">
      登录后即可继续 AI 对话、保存行程，并查看你的历史会话。
    </p>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="auth-form"
    >
      <el-form-item label="手机号" prop="phone">
        <el-input
          v-model="form.phone"
          :prefix-icon="Iphone"
          maxlength="11"
          placeholder="请输入手机号"
          autocomplete="tel"
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          :prefix-icon="Lock"
          placeholder="请输入密码"
          autocomplete="current-password"
          @keyup.enter="handleSubmit"
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        class="auth-submit"
        :loading="submitting"
        @click="handleSubmit"
      >
        登录
      </el-button>
    </el-form>

    <div class="auth-links">
      <RouterLink :to="registerLink" class="auth-link">
        还没有账号？立即注册
      </RouterLink>
      <RouterLink to="/chat" class="auth-link auth-link--muted">
        先逛逛
      </RouterLink>
    </div>

    <template #footnote>
      匿名状态下也可以与 AI 对话；保存行程、打车和查看历史需要登录。
    </template>
  </AuthLayout>
</template>
