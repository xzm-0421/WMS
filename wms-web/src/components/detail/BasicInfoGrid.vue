<script setup lang="ts">
interface BasicItem {
  label: string
  value?: string | number | null
  required?: boolean
  span?: number
  slot?: string
}

const props = withDefaults(defineProps<{ items: BasicItem[]; columns?: number }>(), { columns: 4 })

function display(value: string | number | null | undefined): string {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

function spanOf(item: BasicItem): number {
  const base = Math.max(1, Math.round(24 / props.columns))
  return Math.min(24, base * (item.span && item.span > 0 ? item.span : 1))
}
</script>

<template>
  <el-row :gutter="16" class="basic-grid">
    <el-col v-for="(item, idx) in items" :key="idx" :span="spanOf(item)" class="basic-col">
      <div class="basic-item">
        <span class="basic-label">
          <span v-if="item.required" class="basic-req">*</span>{{ item.label }}
        </span>
        <span class="basic-value">
          <slot v-if="item.slot" :name="item.slot" :item="item" />
          <template v-else>{{ display(item.value) }}</template>
        </span>
      </div>
    </el-col>
  </el-row>
</template>

<style scoped>
.basic-col {
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.basic-item {
  display: flex;
  align-items: flex-start;
  padding: 9px 0;
  font-size: 13px;
  line-height: 1.5;
}
.basic-label {
  flex: none;
  width: 96px;
  padding-right: 8px;
  text-align: right;
  color: var(--el-text-color-secondary);
}
.basic-req {
  margin-right: 2px;
  color: var(--el-color-danger);
}
.basic-value {
  flex: 1;
  min-width: 0;
  word-break: break-all;
}
</style>
