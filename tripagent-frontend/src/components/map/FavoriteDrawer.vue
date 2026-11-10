<script setup lang="ts">
import { computed } from 'vue'
import {
  Delete,
  Lock,
  Refresh,
  Star,
  WarningFilled,
} from '@element-plus/icons-vue'
import type { FavoritePlace } from '@/types/favorite'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    favorites?: FavoritePlace[]
    loading?: boolean
    error?: string
    loggedIn?: boolean
    removingId?: string | null
  }>(),
  {
    favorites: () => [],
    loading: false,
    error: '',
    loggedIn: true,
    removingId: null,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  refresh: []
  remove: [favorite: FavoritePlace]
  login: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
</script>

<template>
  <el-drawer
    v-model="visible"
    title="我的收藏"
    direction="rtl"
    size="min(430px, 92vw)"
    append-to-body
  >
    <div class="favorite-drawer">
      <header class="favorite-head">
        <span>已收藏 {{ favorites.length }} 个地点</span>
        <el-tooltip content="刷新收藏" placement="top">
          <el-button
            circle
            size="small"
            :icon="Refresh"
            :loading="loading"
            :disabled="!loggedIn"
            aria-label="刷新收藏"
            @click="emit('refresh')"
          />
        </el-tooltip>
      </header>

      <div v-if="!loggedIn" class="favorite-state">
        <span class="favorite-state__icon favorite-state__icon--lock">
          <el-icon><Lock /></el-icon>
        </span>
        <strong>登录后查看收藏</strong>
        <p>搜索、收藏和加入行程需要一个 TripAgent 账号。</p>
        <el-button type="primary" round @click="emit('login')">
          去登录
        </el-button>
      </div>

      <div v-else-if="loading && favorites.length === 0" class="favorite-loading">
        <el-skeleton :rows="4" animated />
      </div>

      <div v-else-if="error" class="favorite-state">
        <span class="favorite-state__icon favorite-state__icon--warning">
          <el-icon><WarningFilled /></el-icon>
        </span>
        <strong>收藏加载失败</strong>
        <p>{{ error }}</p>
        <el-button round @click="emit('refresh')">重新加载</el-button>
      </div>

      <div v-else-if="favorites.length === 0" class="favorite-state">
        <span class="favorite-state__icon">
          <el-icon><Star /></el-icon>
        </span>
        <strong>还没有收藏景点</strong>
        <p>在地图搜索结果中打开景点详情，就可以收藏喜欢的景点。</p>
      </div>

      <div v-else class="favorite-list">
        <article
          v-for="favorite in favorites"
          :key="favorite.id"
          class="favorite-item"
        >
          <span class="favorite-item__icon">
            <el-icon><Star /></el-icon>
          </span>
          <div class="favorite-item__main">
            <strong>{{ favorite.name }}</strong>
            <p>
              <span v-if="favorite.city">{{ favorite.city }}</span>
              <span v-if="favorite.category">· {{ favorite.category }}</span>
              <span v-if="favorite.address">· {{ favorite.address }}</span>
            </p>
            <small v-if="favorite.note">{{ favorite.note }}</small>
          </div>
          <el-tooltip content="取消收藏" placement="top">
            <el-button
              text
              circle
              size="small"
              type="danger"
              :icon="Delete"
              :loading="removingId === favorite.id"
              :disabled="Boolean(removingId)"
              aria-label="取消收藏"
              @click="emit('remove', favorite)"
            />
          </el-tooltip>
        </article>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.favorite-drawer {
  padding-bottom: 14px;
}

.favorite-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--ta-ink-muted);
  font-size: 10.5px;
}

.favorite-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  padding: 46px 20px;
  text-align: center;
}

.favorite-state__icon {
  display: grid;
  width: 46px;
  height: 46px;
  margin-bottom: 5px;
  border-radius: 14px;
  color: #d39a27;
  font-size: 22px;
  place-items: center;
  background: #fff6e5;
}

.favorite-state__icon--lock {
  color: var(--ta-primary);
  background: #eaf4ff;
}

.favorite-state__icon--warning {
  color: #c9821d;
  background: #fff6e8;
}

.favorite-state strong {
  color: var(--ta-ink);
  font-size: 13px;
  font-weight: 600;
}

.favorite-state p {
  max-width: 320px;
  color: var(--ta-ink-muted);
  font-size: 11px;
  line-height: 1.7;
}

.favorite-state .el-button {
  margin-top: 5px;
}

.favorite-loading {
  padding: 8px 2px;
}

.favorite-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.favorite-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px;
  border: 1px solid #eaf2fb;
  border-radius: 12px;
  background: #fbfdff;
}

.favorite-item__icon {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: #d39a27;
  font-size: 15px;
  place-items: center;
  background: #fff6e5;
}

.favorite-item__main {
  flex: 1;
  min-width: 0;
}

.favorite-item__main strong {
  display: block;
  overflow: hidden;
  color: var(--ta-ink);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.favorite-item__main p {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 2px;
  color: var(--ta-ink-muted);
  font-size: 10px;
  line-height: 1.6;
}

.favorite-item__main small {
  display: block;
  margin-top: 2px;
  color: #8d7b5d;
  font-size: 9.5px;
  line-height: 1.6;
}
</style>
