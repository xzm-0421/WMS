<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const props = defineProps<{
  title: string
  code?: string
  status?: string | number | null
  backTo?: string
}>()

const router = useRouter()

function goBack() {
  if (props.backTo) {
    router.push(props.backTo)
  } else {
    router.back()
  }
}
</script>

<template>
  <div class="detail-page">
    <div class="detail-header">
      <el-button link :icon="ArrowLeft" @click="goBack">返回</el-button>
      <span class="detail-title">{{ title }}</span>
      <span v-if="code" class="detail-code">{{ code }}</span>
      <OrderStatusTag v-if="status !== undefined && status !== null && status !== ''" :status="status" />
      <slot name="header-extra" />
    </div>
    <div class="detail-body">
      <el-card shadow="never" class="detail-basic">
        <slot />
      </el-card>
      <div v-if="$slots.tabs" class="detail-tabs">
        <el-card shadow="never">
          <slot name="tabs" />
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: var(--el-bg-color-page, #f5f7fa);
}
.detail-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: var(--el-bg-color, #fff);
  border-bottom: 1px solid var(--el-border-color-light);
}
.detail-title {
  font-size: 16px;
  font-weight: 600;
}
.detail-code {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 20px 32px;
}
</style>
