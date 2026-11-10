<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Iphone, Lock, Message, User } from '@element-plus/icons-vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { register } from '@/api/auth'
import type { RegisterPayload } from '@/types/auth'

interface RegisterForm {
  phone: string
  nickname: string
  email: string
  password: string
  confirmPassword: string
}

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<RegisterForm>({
  phone: typeof route.query.phone === 'string' ? route.query.phone : '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const rules: FormRules<RegisterForm> = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入有效的中国大陆手机号',
      trigger: 'blur',
    },
  ],
  nickname: [
    {
      max: 32,
      message: '昵称最长 32 个字符',
      trigger: 'blur',
    },
  ],
  email: [
    {
      type: 'email',
      message: '请输入有效的邮箱地址',
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
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

const loginLink = computed(() => {
  const query: Record<string, string> = {}
  if (typeof route.query.redirect === 'string') {
    query.redirect = route.query.redirect
  }
  if (form.phone) {
    query.phone = form.phone
  }
  return { name: 'login', query }
})

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
    const payload: RegisterPayload = {
      phone: form.phone,
      password: form.password,
    }

    const nickname = form.nickname.trim()
    const email = form.email.trim()
    if (nickname) {
      payload.nickname = nickname
    }
    if (email) {
      payload.email = email
    }

    await register(payload)
    ElMessage.success('注册成功，请登录')

    const query: Record<string, string> = { phone: form.phone }
    if (typeof route.query.redirect === 'string') {
      query.redirect = route.query.redirect
    }
    await router.replace({ name: 'login', query })
  } catch {
    // 注册失败信息已由请求层统一提示（如 40901 手机号/邮箱已存在）。
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <h2>创建账号</h2>
    <p class="auth-card__desc">
      注册 TripAgent 账号，保存你的对话、行程与旅行偏好。
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

      <el-form-item label="昵称（可选）" prop="nickname">
        <el-input
          v-model="form.nickname"
          :prefix-icon="User"
          maxlength="32"
          placeholder="请输入昵称"
          autocomplete="nickname"
        />
      </el-form-item>

      <el-form-item label="邮箱（可选）" prop="email">
        <el-input
          v-model="form.email"
          :prefix-icon="Message"
          placeholder="请输入邮箱"
          autocomplete="email"
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          :prefix-icon="Lock"
          placeholder="8-64 位，需包含字母和数字"
          autocomplete="new-password"
        />
      </el-form-item>

      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          :prefix-icon="Lock"
          placeholder="请再次输入密码"
          autocomplete="new-password"
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
        注册并前往登录
      </el-button>
    </el-form>

    <div class="auth-links">
      <RouterLink :to="loginLink" class="auth-link">
        已有账号？返回登录
      </RouterLink>
      <RouterLink to="/chat" class="auth-link auth-link--muted">
        先逛逛
      </RouterLink>
    </div>

    <template #footnote>
      注册成功后返回登录页；密码只提交给 Java 后端，不会保存在前端。
    </template>
  </AuthLayout>
</template>
