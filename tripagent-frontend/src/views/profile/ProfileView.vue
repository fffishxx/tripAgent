<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import {
  Calendar,
  ChatLineRound,
  Check,
  EditPen,
  Iphone,
  Key,
  Lock,
  Message,
  Star,
  SwitchButton,
  Tickets,
  Upload,
  User,
  WarningFilled,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { fetchCurrentUser, updateCurrentUser } from '@/api/auth'
import { getFavorites } from '@/api/favorites'
import { getPreferences, updatePreferences } from '@/api/preference'
import { getSessionHistory } from '@/api/agent'
import { getTaxiOrders } from '@/api/taxi'
import { getTrips } from '@/api/trips'
import { uploadAvatar } from '@/api/upload'
import { useLogout } from '@/composables/useLogout'
import { useAuthStore } from '@/stores/auth'
import type { UserProfile } from '@/types/auth'
import type { UserPreference } from '@/types/preference'
import { formatDateTime, maskPhone } from '@/utils/format'
import {
  BUDGET_OPTIONS,
  CROWD_OPTIONS,
  FOOD_SUGGESTIONS,
  INTEREST_SUGGESTIONS,
  PACE_OPTIONS,
  TRANSPORT_OPTIONS,
  createEmptyPreference,
  sanitizeTags,
} from '@/utils/preference'

interface EditForm {
  nickname: string
  email: string
  avatarUrl: string
}

interface ProfileStats {
  trips: number | null
  history: number | null
  orders: number | null
  favorites: number | null
}

const auth = useAuthStore()
const { logout } = useLogout()

const profile = ref<UserProfile | null>(
  auth.user ? ({ ...auth.user } as UserProfile) : null,
)
const profileLoading = ref(true)
const profileError = ref('')

const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const avatarUploading = ref(false)
const editFormRef = ref<FormInstance>()
const avatarFileInput = ref<HTMLInputElement | null>(null)

const editForm = reactive<EditForm>({
  nickname: '',
  email: '',
  avatarUrl: '',
})

const isEditDirty = computed(() => {
  const nickname = editForm.nickname.trim()
  const email = editForm.email.trim()
  const avatar = editForm.avatarUrl.trim()
  return (
    nickname !== (profile.value?.nickname ?? '').trim() ||
    email !== (profile.value?.email ?? '').trim() ||
    avatar !== (profile.value?.avatarUrl ?? '').trim()
  )
})

const editRules: FormRules<EditForm> = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 32, message: '昵称不能超过 32 个字符', trigger: 'blur' },
  ],
  email: [
    {
      validator: (_rule, value: string, callback) => {
        const text = (value ?? '').trim()
        if (!text) {
          callback()
          return
        }
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text)) {
          callback(new Error('邮箱格式不正确'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
    { max: 128, message: '邮箱不能超过 128 个字符', trigger: 'blur' },
  ],
  avatarUrl: [
    { max: 512, message: '头像地址不能超过 512 个字符', trigger: 'blur' },
    {
      pattern: /^$|^https?:\/\/\S+$/i,
      message: '请输入以 http:// 或 https:// 开头的图片地址',
      trigger: 'blur',
    },
  ],
}

const preference = reactive<UserPreference>(createEmptyPreference())
const preferenceLoading = ref(true)
const preferenceError = ref('')
const preferenceSaving = ref(false)
const preferenceBaseline = ref('')

function preferenceSignature(): string {
  return JSON.stringify({
    crowd: preference.crowd,
    budgetLevel: preference.budgetLevel,
    preferredTransport: preference.preferredTransport,
    travelPace: preference.travelPace,
    interests: preference.interests
      .map((item) => item.trim())
      .filter(Boolean),
    foodPreferences: preference.foodPreferences
      .map((item) => item.trim())
      .filter(Boolean),
    accommodationPreference: (
      preference.accommodationPreference ?? ''
    ).trim(),
    notes: (preference.notes ?? '').trim(),
  })
}

const isPreferenceDirty = computed(
  () =>
    preferenceBaseline.value !== '' &&
    preferenceSignature() !== preferenceBaseline.value,
)

const hasUnsavedChanges = computed(
  () =>
    (editDialogVisible.value && isEditDirty.value) ||
    isPreferenceDirty.value,
)

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (hasUnsavedChanges.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(async () => {
  if (!hasUnsavedChanges.value) {
    return true
  }

  try {
    await ElMessageBox.confirm(
      '当前有未保存的资料或旅行偏好修改，离开后这些修改会丢失。',
      '离开个人中心？',
      {
        confirmButtonText: '离开',
        cancelButtonText: '继续编辑',
        type: 'warning',
      },
    )
    return true
  } catch {
    return false
  }
})

const stats = reactive<ProfileStats>({
  trips: null,
  history: null,
  orders: null,
  favorites: null,
})
const statsLoading = ref(true)
const statsError = ref('')

const displayName = computed(
  () => profile.value?.nickname || auth.displayName,
)
const avatarUrl = computed(
  () => profile.value?.avatarUrl || auth.user?.avatarUrl || null,
)
const maskedPhone = computed(() =>
  profile.value?.phone ? maskPhone(profile.value.phone) : '--',
)
const profileEmail = computed(() => profile.value?.email || '未绑定邮箱')
const shortUserId = computed(() => {
  const userId = profile.value?.userId ?? ''
  return userId.length > 18 ? `${userId.slice(0, 18)}…` : userId || '--'
})
const isActiveAccount = computed(
  () => profile.value?.status === undefined || profile.value.status === 1,
)

const avatarPreviewName = computed(
  () => editForm.nickname.trim() || displayName.value,
)

function statValue(value: number | null): string {
  if (statsLoading.value) {
    return '…'
  }
  return value === null ? '--' : String(value)
}

async function loadProfile(): Promise<void> {
  profileLoading.value = true
  profileError.value = ''
  try {
    const loaded = await fetchCurrentUser()
    profile.value = loaded
    auth.setUser(loaded)
  } catch (error) {
    profileError.value =
      error instanceof Error ? error.message : '用户资料加载失败'
  } finally {
    profileLoading.value = false
  }
}

async function loadPreferences(): Promise<void> {
  preferenceLoading.value = true
  preferenceError.value = ''
  try {
    const loaded = await getPreferences()
    preference.crowd = loaded.crowd || 'general'
    preference.budgetLevel = loaded.budgetLevel || 'economy'
    preference.preferredTransport =
      loaded.preferredTransport || 'transit'
    preference.travelPace = loaded.travelPace || 'moderate'
    preference.interests = Array.isArray(loaded.interests)
      ? [...loaded.interests]
      : []
    preference.foodPreferences = Array.isArray(loaded.foodPreferences)
      ? [...loaded.foodPreferences]
      : []
    preference.accommodationPreference =
      loaded.accommodationPreference ?? ''
    preference.notes = loaded.notes ?? ''
    preferenceBaseline.value = preferenceSignature()
  } catch (error) {
    preferenceError.value =
      error instanceof Error ? error.message : '旅行偏好加载失败'
  } finally {
    preferenceLoading.value = false
  }
}

async function loadStats(): Promise<void> {
  statsLoading.value = true
  statsError.value = ''

  try {
    const results = await Promise.allSettled([
      getTrips({ pageNum: 1, pageSize: 1 }, { silent: true }),
      getSessionHistory({ silent: true }),
      getTaxiOrders({ pageNum: 1, pageSize: 1 }, { silent: true }),
      getFavorites({ pageNum: 1, pageSize: 1 }, { silent: true }),
    ])

    const [tripsResult, historyResult, ordersResult, favoritesResult] = results

    stats.trips =
      tripsResult.status === 'fulfilled' ? tripsResult.value.total : null
    stats.orders =
      ordersResult.status === 'fulfilled' ? ordersResult.value.total : null
    stats.favorites =
      favoritesResult.status === 'fulfilled'
        ? favoritesResult.value.total
        : null
    stats.history =
      historyResult.status === 'fulfilled'
        ? Object.values(historyResult.value ?? {}).reduce(
            (total, sessions) => total + (sessions?.length ?? 0),
            0,
          )
        : null

    const failedCount = results.filter(
      (result) => result.status === 'rejected',
    ).length
    if (failedCount > 0) {
      statsError.value =
        failedCount === results.length
          ? '统计加载失败'
          : '部分统计加载失败'
    }
  } finally {
    statsLoading.value = false
  }
}

onMounted(() => {
  void loadProfile()
  void loadPreferences()
  void loadStats()
})

function openEditDialog(): void {
  editForm.nickname = profile.value?.nickname ?? ''
  editForm.email = profile.value?.email ?? ''
  editForm.avatarUrl = profile.value?.avatarUrl ?? ''
  editDialogVisible.value = true
  nextTick(() => editFormRef.value?.clearValidate())
}

async function handleEditDialogClose(done: () => void): Promise<void> {
  if (editSubmitting.value || avatarUploading.value) {
    ElMessage.warning('正在保存或上传头像，请稍候')
    return
  }

  if (!isEditDirty.value) {
    done()
    return
  }

  try {
    await ElMessageBox.confirm(
      '当前资料修改尚未保存，关闭后修改会丢失。',
      '放弃修改？',
      {
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
        type: 'warning',
      },
    )
    done()
  } catch {
    // 继续编辑。
  }
}

function cancelEditDialog(): void {
  void handleEditDialogClose(() => {
    editDialogVisible.value = false
  })
}

function triggerAvatarUpload(): void {
  avatarFileInput.value?.click()
}

async function handleAvatarFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }

  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    ElMessage.warning('头像仅支持 JPG、PNG、WEBP 或 GIF 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像不能超过 5MB')
    return
  }

  avatarUploading.value = true
  try {
    const uploaded = await uploadAvatar(file)
    editForm.avatarUrl = uploaded.url
    ElMessage.success('头像上传成功，保存资料后生效')
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : '头像上传失败',
    )
  } finally {
    avatarUploading.value = false
  }
}

async function submitProfile(): Promise<void> {
  if (!editFormRef.value || editSubmitting.value) {
    return
  }

  let valid = false
  try {
    valid = await editFormRef.value.validate()
  } catch {
    valid = false
  }
  if (!valid) {
    return
  }

  editSubmitting.value = true
  try {
    const updated = await updateCurrentUser({
      nickname: editForm.nickname.trim(),
      email: editForm.email.trim(),
      avatarUrl: editForm.avatarUrl.trim(),
    })
    profile.value = updated
    auth.setUser(updated)
    editDialogVisible.value = false
    ElMessage.success('个人资料已保存')
  } catch {
    // 邮箱冲突等错误由请求层统一提示。
  } finally {
    editSubmitting.value = false
  }
}

async function savePreferences(): Promise<void> {
  if (preferenceSaving.value) {
    return
  }

  const interests = sanitizeTags(
    preference.interests,
    20,
    '兴趣',
  )
  if (interests.error) {
    ElMessage.warning(interests.error)
    return
  }
  const foods = sanitizeTags(
    preference.foodPreferences,
    50,
    '饮食偏好',
  )
  if (foods.error) {
    ElMessage.warning(foods.error)
    return
  }

  const accommodation = (
    preference.accommodationPreference ?? ''
  ).trim()
  const notes = (preference.notes ?? '').trim()
  if (accommodation.length > 255) {
    ElMessage.warning('住宿偏好不能超过 255 个字符')
    return
  }
  if (notes.length > 1000) {
    ElMessage.warning('补充说明不能超过 1000 个字符')
    return
  }

  preferenceSaving.value = true
  try {
    const updated = await updatePreferences({
      crowd: preference.crowd,
      budgetLevel: preference.budgetLevel,
      preferredTransport: preference.preferredTransport,
      travelPace: preference.travelPace,
      interests: interests.values,
      foodPreferences: foods.values,
      accommodationPreference: accommodation || null,
      notes: notes || null,
    })

    preference.crowd = updated.crowd
    preference.budgetLevel = updated.budgetLevel
    preference.preferredTransport = updated.preferredTransport
    preference.travelPace = updated.travelPace
    preference.interests = [...updated.interests]
    preference.foodPreferences = [...updated.foodPreferences]
    preference.accommodationPreference =
      updated.accommodationPreference ?? ''
    preference.notes = updated.notes ?? ''
    preferenceBaseline.value = preferenceSignature()
    ElMessage.success('旅行偏好已保存')
  } catch {
    // 保存失败信息由请求层统一提示。
  } finally {
    preferenceSaving.value = false
  }
}
</script>

<template>
  <div class="page profile-page">
    <PageHeader
      title="个人中心"
      description="管理账号资料、旅行偏好和账户安全。"
      :icon="User"
    />

    <el-alert
      v-if="profileError"
      type="warning"
      :closable="false"
      show-icon
      title="个人资料加载失败"
    >
      <div class="profile-alert-body">
        <span>{{ profileError }}，当前显示的是本地缓存资料。</span>
        <el-button text size="small" @click="loadProfile">
          重新加载
        </el-button>
      </div>
    </el-alert>

    <section class="profile-hero surface-card">
      <UserAvatar
        :name="displayName"
        :src="avatarUrl"
        :size="84"
        shape="rounded"
      />

      <div class="profile-hero__main">
        <div class="profile-hero__name-row">
          <h2>{{ displayName }}</h2>
          <span
            class="profile-status"
            :class="{ 'profile-status--disabled': !isActiveAccount }"
          >
            {{ isActiveAccount ? '正常' : '已禁用' }}
          </span>
          <span class="stage-badge">TripAgent 用户</span>
        </div>

        <div class="profile-hero__meta">
          <span>
            <el-icon><Iphone /></el-icon>
            {{ maskedPhone }}
          </span>
          <span>
            <el-icon><Message /></el-icon>
            {{ profileEmail }}
          </span>
          <span>
            <el-icon><User /></el-icon>
            ID {{ shortUserId }}
          </span>
        </div>

        <p class="profile-hero__hint">
          <template v-if="profileLoading">
            正在同步最新资料…
          </template>
          <template v-else>
            加入于
            {{ profile?.createTime ? formatDateTime(profile.createTime) : '--' }}
            <template v-if="profile?.lastLoginTime">
              · 最近登录 {{ formatDateTime(profile.lastLoginTime) }}
            </template>
          </template>
        </p>
      </div>

      <div class="profile-hero__side">
        <el-button
          size="small"
          :icon="EditPen"
          @click="openEditDialog"
        >
          编辑资料
        </el-button>
      </div>
    </section>

    <div class="profile-stats">
      <StatCard
        label="我的行程"
        :value="statValue(stats.trips)"
        hint="全部行程"
        :icon="Calendar"
        tone="blue"
      />
      <StatCard
        label="历史会话"
        :value="statValue(stats.history)"
        hint="云端会话归档"
        :icon="ChatLineRound"
        tone="green"
      />
      <StatCard
        label="我的订单"
        :value="statValue(stats.orders)"
        hint="含模拟打车订单"
        :icon="Tickets"
        tone="amber"
      />
      <StatCard
        label="收藏景点"
        :value="statValue(stats.favorites)"
        hint="来自地图探索"
        :icon="Star"
        tone="violet"
      />
    </div>

    <div v-if="statsLoading || statsError" class="profile-stats-status">
      <template v-if="statsLoading">
        <span class="profile-stats-status__spinner" aria-hidden="true" />
        正在加载统计…
      </template>
      <template v-else>
        <el-icon><WarningFilled /></el-icon>
        {{ statsError }}
        <el-button text size="small" @click="loadStats">重试</el-button>
      </template>
    </div>

    <section class="preference-card surface-card">
      <header class="section-head">
        <div>
          <div class="section-head__title-row">
            <h2>旅行偏好</h2>
            <span class="stage-badge">用于 AI 规划</span>
          </div>
          <p>
            只使用你在这里主动设置的数据，不会从聊天内容自动提取；Agent 规划时由 Java 后端读取。
          </p>
        </div>
        <el-button
          type="primary"
          :icon="Check"
          :loading="preferenceSaving"
          :disabled="preferenceLoading || Boolean(preferenceError)"
          @click="savePreferences"
        >
          保存偏好
        </el-button>
      </header>

      <div v-if="preferenceLoading" class="preference-loading">
        <el-skeleton :rows="4" animated />
      </div>

      <div v-else-if="preferenceError" class="preference-error">
        <span>
          <el-icon><WarningFilled /></el-icon>
          {{ preferenceError }}
        </span>
        <el-button text size="small" @click="loadPreferences">
          重新加载
        </el-button>
      </div>

      <template v-else>
        <div class="preference-grid">
          <label class="preference-field">
            <span>出行人群</span>
            <el-select v-model="preference.crowd">
              <el-option
                v-for="option in CROWD_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </label>

          <label class="preference-field">
            <span>预算等级</span>
            <el-select v-model="preference.budgetLevel">
              <el-option
                v-for="option in BUDGET_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </label>

          <label class="preference-field">
            <span>交通方式</span>
            <el-select v-model="preference.preferredTransport">
              <el-option
                v-for="option in TRANSPORT_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </label>

          <label class="preference-field">
            <span>行程节奏</span>
            <el-select v-model="preference.travelPace">
              <el-option
                v-for="option in PACE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </label>
        </div>

        <div class="preference-tags-grid">
          <label class="preference-field">
            <span>兴趣偏好</span>
            <el-select
              v-model="preference.interests"
              multiple
              filterable
              allow-create
              default-first-option
              collapse-tags
              collapse-tags-tooltip
              :multiple-limit="10"
              :reserve-keyword="false"
              placeholder="选择或输入兴趣后回车"
            >
              <el-option
                v-for="interest in INTEREST_SUGGESTIONS"
                :key="interest"
                :label="interest"
                :value="interest"
              />
            </el-select>
            <small>最多 10 项，每项不超过 20 个字符。</small>
          </label>

          <label class="preference-field">
            <span>饮食偏好</span>
            <el-select
              v-model="preference.foodPreferences"
              multiple
              filterable
              allow-create
              default-first-option
              collapse-tags
              collapse-tags-tooltip
              :multiple-limit="10"
              :reserve-keyword="false"
              placeholder="选择或输入饮食偏好后回车"
            >
              <el-option
                v-for="food in FOOD_SUGGESTIONS"
                :key="food"
                :label="food"
                :value="food"
              />
            </el-select>
            <small>最多 10 项，每项不超过 50 个字符。</small>
          </label>
        </div>

        <div class="preference-grid preference-grid--single">
          <label class="preference-field">
            <span>住宿偏好</span>
            <el-input
              v-model="preference.accommodationPreference"
              maxlength="255"
              placeholder="例如：靠近地铁站、安静、家庭房"
            />
          </label>
        </div>

        <label class="preference-field preference-field--notes">
          <span>补充说明</span>
          <el-input
            v-model="preference.notes"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            resize="none"
            placeholder="例如：带老人，行程不要太赶；有食物过敏等。"
          />
        </label>

        <p class="preference-footnote">
          保存后，后续 AI 行程规划会在后端读取这些偏好。
        </p>
      </template>
    </section>

    <section class="account-card surface-card">
      <header class="section-head">
        <div>
          <div class="section-head__title-row">
            <h2>账户安全</h2>
            <span class="stage-badge">基础资料</span>
          </div>
          <p>账号密码与手机号变更能力暂未开放。</p>
        </div>
      </header>

      <div class="security-list">
        <div class="security-row">
          <span class="security-row__icon"><el-icon><Iphone /></el-icon></span>
          <div>
            <strong>登录手机号</strong>
            <p>{{ maskedPhone }}（手机号不可在本次演示中修改）</p>
          </div>
          <span class="security-tag">不可修改</span>
        </div>

        <div class="security-row">
          <span class="security-row__icon"><el-icon><Key /></el-icon></span>
          <div>
            <strong>修改密码</strong>
            <p>当前后端暂未提供改密接口。</p>
          </div>
          <span class="security-tag">暂未开放</span>
        </div>

        <div class="security-row">
          <span class="security-row__icon"><el-icon><Lock /></el-icon></span>
          <div>
            <strong>更换手机号</strong>
            <p>手机号变更需要短信验证，当前版本不提供。</p>
          </div>
          <span class="security-tag">暂未开放</span>
        </div>

        <div class="security-row security-row--action">
          <span class="security-row__icon security-row__icon--danger">
            <el-icon><SwitchButton /></el-icon>
          </span>
          <div>
            <strong>退出登录</strong>
            <p>退出后可以匿名继续使用 AI 对话。</p>
          </div>
          <el-button type="danger" plain @click="logout">
            退出登录
          </el-button>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="editDialogVisible"
      title="编辑个人资料"
      width="min(540px, 94vw)"
      :close-on-click-modal="false"
      :before-close="handleEditDialogClose"
      append-to-body
    >
      <div class="edit-avatar">
        <UserAvatar
          :name="avatarPreviewName"
          :src="editForm.avatarUrl || null"
          :size="72"
          shape="rounded"
        />
        <div class="edit-avatar__actions">
          <el-button
            :icon="Upload"
            :loading="avatarUploading"
            @click="triggerAvatarUpload"
          >
            上传新头像
          </el-button>
          <small>支持 JPG/PNG/WEBP/GIF，最大 5MB；由 Java 后端上传到 OSS。</small>
        </div>
        <input
          ref="avatarFileInput"
          class="hidden-file-input"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          @change="handleAvatarFileChange"
        />
      </div>

      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-position="top"
        class="edit-profile-form"
      >
        <el-form-item label="昵称" prop="nickname">
          <el-input
            v-model="editForm.nickname"
            maxlength="32"
            show-word-limit
            placeholder="请输入昵称"
          />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="editForm.email"
            maxlength="128"
            placeholder="选填，例如 name@example.com"
          />
        </el-form-item>

        <el-form-item label="头像地址" prop="avatarUrl">
          <el-input
            v-model="editForm.avatarUrl"
            maxlength="512"
            placeholder="上传后自动填入，也可粘贴 https 图片地址"
          />
        </el-form-item>

        <div class="edit-phone-row">
          <span>登录手机号</span>
          <strong>{{ maskedPhone }}</strong>
          <small>手机号暂不支持修改</small>
        </div>
      </el-form>

      <template #footer>
        <el-button :disabled="editSubmitting" @click="cancelEditDialog">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="editSubmitting"
          :disabled="avatarUploading"
          @click="submitProfile"
        >
          保存资料
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 1180px;
  margin: 0 auto;
}

.profile-hero {
  position: relative;
  display: flex;
  align-items: center;
  gap: 19px;
  padding: 24px 26px;
  overflow: hidden;
  background:
    radial-gradient(circle at 92% 8%, rgba(117, 174, 255, 0.18), transparent 34%),
    rgba(255, 255, 255, 0.94);
}

.profile-hero__main {
  flex: 1;
  min-width: 0;
}

.profile-hero__name-row {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

.profile-hero__name-row h2 {
  color: var(--ta-ink-strong);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.025em;
}

.profile-status {
  padding: 2px 8px;
  border-radius: 999px;
  color: #1e835c;
  font-size: 9.5px;
  font-weight: 500;
  background: #e9f8f1;
}

.profile-status--disabled {
  color: #a44f42;
  background: #fff0ed;
}

.profile-hero__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 13px;
  margin-top: 8px;
  color: var(--ta-ink-secondary);
  font-size: 10.5px;
}

.profile-hero__meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.profile-hero__hint {
  margin-top: 7px;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.profile-hero__side {
  flex: 0 0 auto;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.profile-alert-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.profile-stats-status {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: -4px;
  color: var(--ta-ink-muted);
  font-size: 10px;
}

.profile-stats-status__spinner {
  width: 10px;
  height: 10px;
  border: 1.5px solid #cfe3fa;
  border-top-color: var(--ta-primary);
  border-radius: 50%;
  animation: profile-stats-spin 0.8s linear infinite;
}

@keyframes profile-stats-spin {
  to {
    transform: rotate(360deg);
  }
}

.preference-card,
.account-card {
  padding: 22px 24px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 17px;
}

.section-head__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.section-head h2 {
  color: var(--ta-ink);
  font-size: 16px;
  font-weight: 600;
}

.section-head p {
  margin-top: 4px;
  color: var(--ta-ink-muted);
  font-size: 11px;
  line-height: 1.7;
}

.preference-loading {
  padding: 8px 2px;
}

.preference-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px;
  border: 1px solid #f1e1d8;
  border-radius: 10px;
  color: #9a6a5c;
  font-size: 11px;
  background: #fff9f7;
}

.preference-error span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.preference-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 13px;
}

.preference-tags-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px;
  margin-top: 15px;
}

.preference-grid--single {
  grid-template-columns: minmax(0, 1fr);
  margin-top: 15px;
}

.preference-field {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 5px;
  color: var(--ta-ink-secondary);
  font-size: 11.5px;
  font-weight: 500;
}

.preference-field small {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  font-weight: 400;
  line-height: 1.5;
}

.preference-field :deep(.el-select) {
  width: 100%;
}

.preference-field--notes {
  margin-top: 15px;
}

.preference-footnote {
  margin-top: 13px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.6;
}

.security-list {
  display: flex;
  flex-direction: column;
}

.security-row {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 12px 0;
}

.security-row + .security-row {
  border-top: 1px dashed #eef4fb;
}

.security-row__icon {
  display: grid;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: var(--ta-primary);
  font-size: 16px;
  place-items: center;
  background: #eaf4ff;
}

.security-row__icon--danger {
  color: #d55c67;
  background: #fff0f1;
}

.security-row > div {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.security-row strong {
  color: var(--ta-ink);
  font-size: 11.5px;
  font-weight: 500;
}

.security-row p {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.6;
}

.security-tag {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 999px;
  color: #8a9bb0;
  font-size: 9px;
  background: #f2f6fb;
}

.security-row--action {
  margin-top: 3px;
}

.edit-avatar {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-bottom: 18px;
  padding: 12px;
  border: 1px solid #e8f1fb;
  border-radius: 12px;
  background: #fafdff;
}

.edit-avatar__actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.edit-avatar__actions small {
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  line-height: 1.6;
}

.hidden-file-input {
  display: none;
}

.edit-profile-form :deep(.el-form-item__label) {
  padding-bottom: 5px;
  color: var(--ta-ink-secondary);
  font-size: 12px;
  font-weight: 500;
}

.edit-phone-row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
  background: #f7fbff;
}

.edit-phone-row strong {
  color: var(--ta-ink-secondary);
  font-weight: 500;
}

.edit-phone-row small {
  margin-left: auto;
  color: #a7b7ca;
  font-size: 9px;
}

@media (max-width: 1000px) {
  .profile-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .preference-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .profile-hero {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 18px;
  }

  .profile-hero__main {
    flex-basis: calc(100% - 103px);
  }

  .profile-hero__name-row h2 {
    font-size: 20px;
  }

  .profile-hero__side {
    width: 100%;
    margin-left: 103px;
  }

  .profile-stats {
    grid-template-columns: 1fr;
  }

  .preference-card,
  .account-card {
    padding: 17px 15px;
  }

  .section-head {
    align-items: stretch;
    flex-direction: column;
  }

  .preference-grid,
  .preference-tags-grid {
    grid-template-columns: 1fr;
  }

  .security-row {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .security-tag {
    margin-left: 43px;
  }

  .security-row--action :deep(.el-button) {
    margin-left: 43px;
  }

  .edit-avatar {
    align-items: flex-start;
    flex-wrap: wrap;
  }
}
</style>
