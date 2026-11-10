<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  ChatDotRound,
  Clock,
  Delete,
  EditPen,
  MoreFilled,
  Plus,
  Refresh,
  Search,
  WarningFilled,
} from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  deleteSessionHistory,
  getSessionHistory,
  updateSessionTitle,
} from '@/api/agent'
import type { AgentHistorySession } from '@/types/agent'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const GROUP_ORDER = ['当天', '最近30天', '最近1年', '1年以上']

const history = ref<Record<string, AgentHistorySession[]>>({})
const loading = ref(true)
const loadError = ref('')
const search = ref(typeof route.query.q === 'string' ? route.query.q : '')
const activeGroup = ref(
  typeof route.query.group === 'string' && route.query.group
    ? route.query.group
    : '全部',
)

let queryTimer: ReturnType<typeof setTimeout> | null = null

function syncQuery(): void {
  if (queryTimer !== null) {
    clearTimeout(queryTimer)
  }
  queryTimer = setTimeout(() => {
    const query: Record<string, string> = {}
    const keyword = search.value.trim()
    if (keyword) {
      query.q = keyword
    }
    if (activeGroup.value !== '全部') {
      query.group = activeGroup.value
    }
    void router.replace({ name: 'history', query })
  }, 200)
}

watch([search, activeGroup], syncQuery)

onBeforeUnmount(() => {
  if (queryTimer !== null) {
    clearTimeout(queryTimer)
  }
})

const existingGroups = computed(() =>
  GROUP_ORDER.filter((key) => (history.value[key]?.length ?? 0) > 0),
)

const filterOptions = computed(() => [
  { key: '全部', label: '全部' },
  ...existingGroups.value.map((key) => ({ key, label: key })),
])

const totalCount = computed(() =>
  Object.values(history.value).reduce((total, items) => total + items.length, 0),
)

const filteredGroups = computed(() => {
  const keyword = search.value.trim().toLowerCase()

  return existingGroups.value
    .filter((key) => activeGroup.value === '全部' || activeGroup.value === key)
    .map((key) => ({
      key,
      items: (history.value[key] ?? []).filter(
        (session) =>
          !keyword || session.title.toLowerCase().includes(keyword),
      ),
    }))
    .filter((group) => group.items.length > 0)
})

const filteredCount = computed(() =>
  filteredGroups.value.reduce((total, group) => total + group.items.length, 0),
)

const hasSearchNoResults = computed(
  () =>
    !loading.value &&
    !loadError.value &&
    totalCount.value > 0 &&
    filteredCount.value === 0,
)

const isEmpty = computed(
  () => !loading.value && !loadError.value && totalCount.value === 0,
)

async function fetchHistory(): Promise<void> {
  loading.value = true
  loadError.value = ''

  try {
    const data = await getSessionHistory()
    history.value = data && typeof data === 'object' ? data : {}

    if (
      activeGroup.value !== '全部' &&
      !(history.value[activeGroup.value]?.length)
    ) {
      activeGroup.value = '全部'
    }
  } catch (error) {
    loadError.value =
      error instanceof Error ? error.message : '历史会话加载失败'
    history.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void fetchHistory()
})

function openSession(session: AgentHistorySession): void {
  void router.push({
    name: 'chat-home',
    params: {
      sessionId: session.session_id,
    },
    query: {
      title: session.title,
    },
  })
}

function updateLocalTitle(sessionId: string, title: string): void {
  for (const items of Object.values(history.value)) {
    const session = items.find((item) => item.session_id === sessionId)
    if (session) {
      session.title = title
    }
  }
}

function removeLocalSession(sessionId: string): void {
  for (const key of Object.keys(history.value)) {
    history.value[key] = history.value[key].filter(
      (session) => session.session_id !== sessionId,
    )
  }

  if (
    activeGroup.value !== '全部' &&
    !(history.value[activeGroup.value]?.length)
  ) {
    activeGroup.value = '全部'
  }
}

async function renameSession(session: AgentHistorySession): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入新的会话标题',
      '修改标题',
      {
        inputValue: session.title,
        inputPlaceholder: '最多 100 个字符',
        inputValidator: (input: string) => {
          const trimmed = input?.trim() ?? ''
          if (!trimmed) {
            return '标题不能为空'
          }
          if (trimmed.length > 100) {
            return '标题最长 100 个字符'
          }
          return true
        },
        confirmButtonText: '保存',
        cancelButtonText: '取消',
      },
    )

    const title = value.trim()
    if (title === session.title) {
      return
    }

    const result = await updateSessionTitle(session.session_id, title)
    updateLocalTitle(session.session_id, title)

    if (result?.message === 'No change') {
      ElMessage.info('会话标题未发生变化')
    } else {
      ElMessage.success('会话标题已更新')
    }
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    // 修改失败已由请求层统一提示。
  }
}

async function deleteSession(session: AgentHistorySession): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除会话「${session.title}」后不可恢复，同时会清理该会话的对话上下文。`,
      '删除会话',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )

    const result = await deleteSessionHistory(session.session_id)
    removeLocalSession(session.session_id)

    if (result?.message === 'No change') {
      ElMessage.info('会话未发生变化')
    } else {
      ElMessage.success('会话已删除')
    }
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    // 删除失败已由请求层统一提示。
  }
}

function handleSessionCommand(
  session: AgentHistorySession,
  command: string | number | object,
): void {
  if (command === 'rename') {
    void renameSession(session)
    return
  }
  if (command === 'delete') {
    void deleteSession(session)
  }
}

function clearSearch(): void {
  search.value = ''
  activeGroup.value = '全部'
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="历史会话"
      description="回顾你的每一次旅行咨询，继续未完成的规划。"
      :icon="Clock"
    >
      <template #actions>
        <el-button
          type="primary"
          :icon="Plus"
          @click="router.push('/chat')"
        >
          新建对话
        </el-button>
      </template>
    </PageHeader>

    <section class="history-card surface-card">
      <div class="history-toolbar">
        <el-input
          v-model="search"
          clearable
          :prefix-icon="Search"
          placeholder="搜索会话标题…"
          class="history-search"
        />

        <div class="history-toolbar__right">
          <div class="filter-chips" aria-label="会话时间筛选">
            <button
              v-for="option in filterOptions"
              :key="option.key"
              type="button"
              class="filter-chip"
              :class="{ 'filter-chip--active': activeGroup === option.key }"
              @click="activeGroup = option.key"
            >
              {{ option.label }}
            </button>
          </div>

          <el-tooltip content="刷新历史会话" placement="top">
            <el-button
              circle
              :icon="Refresh"
              :loading="loading"
              aria-label="刷新历史会话"
              @click="fetchHistory"
            />
          </el-tooltip>
        </div>
      </div>

      <div v-if="loading" class="history-loading">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="loadError" class="history-error">
        <span class="history-error__icon">
          <el-icon><WarningFilled /></el-icon>
        </span>
        <strong>历史会话加载失败</strong>
        <p>{{ loadError }}</p>
        <el-button type="primary" round @click="fetchHistory">重新加载</el-button>
      </div>

      <div v-else-if="isEmpty">
        <EmptyState
          :icon="ChatDotRound"
          title="还没有历史会话"
          description="与 TripAgent 开始对话后，已命名并保存的会话会自动出现在这里。"
        >
          <el-button type="primary" round @click="router.push('/chat')">
            去新建对话
          </el-button>
        </EmptyState>
      </div>

      <div v-else-if="hasSearchNoResults">
        <EmptyState
          :icon="Search"
          title="没有找到匹配的会话"
          :description="`没有标题包含“${search.trim()}”的会话，试试其他关键词。`"
        >
          <el-button round @click="clearSearch">清除筛选</el-button>
        </EmptyState>
      </div>

      <div v-else class="history-groups">
        <section
          v-for="group in filteredGroups"
          :key="group.key"
          class="history-group"
        >
          <header class="history-group__head">
            <h2>{{ group.key }}</h2>
            <span>{{ group.items.length }}</span>
          </header>

          <div
            v-for="session in group.items"
            :key="session.session_id"
            class="session-row"
            role="button"
            tabindex="0"
            :aria-label="`打开会话：${session.title}`"
            :data-session-id="session.session_id"
            @click="openSession(session)"
            @keydown.enter.prevent="openSession(session)"
            @keydown.space.prevent="openSession(session)"
          >
            <span class="session-row__icon">
              <el-icon><ChatDotRound /></el-icon>
            </span>

            <span class="session-row__content">
              <strong>{{ session.title }}</strong>
              <small>点击继续这段对话</small>
            </span>

            <span class="session-row__time">
              {{ formatDateTime(session.update_time) }}
            </span>

            <el-icon class="session-row__arrow"><ArrowRight /></el-icon>

            <el-dropdown
              trigger="click"
              placement="bottom-end"
              @command="handleSessionCommand(session, $event)"
            >
              <button
                type="button"
                class="session-row__more"
                aria-label="会话操作"
                @click.stop
              >
                <el-icon><MoreFilled /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename" :icon="EditPen">
                    修改标题
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" :icon="Delete" divided>
                    删除会话
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

<style scoped>
.history-card {
  overflow: hidden;
}

.history-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  padding: 18px 22px;
  border-bottom: 1px solid #eef5fd;
}

.history-search {
  flex: 1 1 260px;
  max-width: 380px;
}

.history-toolbar__right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.history-loading {
  padding: 28px 30px 38px;
}

.history-error {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  padding: 54px 24px;
  text-align: center;
}

.history-error__icon {
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 6px;
  border-radius: 15px;
  color: #c9821d;
  font-size: 22px;
  place-items: center;
  background: #fff6e8;
}

.history-error strong {
  color: var(--ta-ink);
  font-size: 14px;
  font-weight: 600;
}

.history-error p {
  max-width: 420px;
  color: var(--ta-ink-muted);
  font-size: 12px;
  line-height: 1.7;
}

.history-error .el-button {
  margin-top: 8px;
}

.history-groups {
  padding: 8px 22px 24px;
}

.history-group + .history-group {
  margin-top: 14px;
}

.history-group__head {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 15px 4px 9px;
}

.history-group__head h2 {
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
}

.history-group__head span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  color: var(--ta-ink-muted);
  font-size: 9.5px;
  background: #f0f6fd;
}

.session-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 13px 14px;
  border: 1px solid transparent;
  border-radius: 13px;
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.session-row + .session-row {
  margin-top: 2px;
}

.session-row:hover {
  border-color: #e6f0fb;
  background: #f8fbff;
  box-shadow: 0 8px 20px rgba(47, 100, 170, 0.06);
}

.session-row__icon {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: var(--ta-primary);
  font-size: 18px;
  place-items: center;
  background: #eaf4ff;
}

.session-row__content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.session-row__content strong {
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-row__content small {
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.session-row__time {
  flex: 0 0 auto;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.session-row__arrow {
  flex: 0 0 auto;
  color: #b7c9df;
  font-size: 12px;
}

.session-row__more {
  display: grid;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 8px;
  color: #93a8c1;
  font-size: 16px;
  place-items: center;
  background: transparent;
  cursor: pointer;
  transition:
    color 0.18s ease,
    background 0.18s ease;
}

.session-row__more:hover {
  color: var(--ta-primary);
  background: #eaf4ff;
}

@media (max-width: 760px) {
  .history-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .history-search {
    flex: 0 0 auto;
    width: 100%;
    max-width: none;
  }

  .history-toolbar__right {
    justify-content: space-between;
  }

  .history-groups {
    padding: 4px 12px 18px;
  }

  .session-row {
    gap: 9px;
    padding: 11px 9px;
  }

  .session-row__icon {
    width: 34px;
    height: 34px;
    font-size: 16px;
  }

  .session-row__time {
    display: none;
  }

  .session-row__arrow {
    display: none;
  }
}
</style>
