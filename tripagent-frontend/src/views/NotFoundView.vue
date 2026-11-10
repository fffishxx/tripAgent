<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Compass } from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
</script>

<template>
  <div class="page not-found">
    <section class="surface-card">
      <EmptyState
        :icon="Compass"
        title="页面走丢了"
        description="你访问的路径不存在，或者已被移动。回到工作台继续规划旅程吧。"
      >
        <el-button type="primary" round @click="router.push('/chat')">
          回到新建对话
        </el-button>
      </EmptyState>
    </section>
  </div>
</template>

<style scoped>
.not-found {
  max-width: 900px;
  margin: 40px auto 0;
}

.not-found .surface-card {
  overflow: hidden;
}
</style>
