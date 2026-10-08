<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface SequenceColumn {
  prop: string
  label: string
  width?: number
  minWidth?: number
  formatter?: (row: Record<string, unknown>) => string
}

interface TreeNode {
  key: string | number
  label: string
  raw?: Record<string, unknown>
  children?: TreeNode[]
}

const props = withDefaults(
  defineProps<{
    ops: Record<string, unknown>[]
    columns: SequenceColumn[]
    loading?: boolean
    title?: string
    rowKey?: string
  }>(),
  { loading: false, title: '主干序列', rowKey: 'seqNo' },
)

const keyword = ref('')
const selectedKey = ref<string | number | null>(null)

function keyOf(op: Record<string, unknown>, index: number): string | number {
  const value = op[props.rowKey]
  return value === undefined || value === null ? index : (value as string | number)
}

const filteredOps = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return props.ops
  return props.ops.filter((op) => {
    const name = String(op.processName ?? '').toLowerCase()
    const code = String(op.processCode ?? '').toLowerCase()
    const seq = String(op.seqNo ?? '')
    return name.includes(kw) || code.includes(kw) || seq.includes(kw)
  })
})

const treeData = computed<TreeNode[]>(() => [
  {
    key: '__root__',
    label: props.title,
    children: filteredOps.value.map((op, index) => ({
      key: keyOf(op, index),
      label: `${op.seqNo ?? ''} ${op.processName ?? op.processCode ?? ''}`.trim(),
      raw: op,
    })),
  },
])

const tableData = computed(() => {
  if (selectedKey.value === null) return filteredOps.value
  const found = filteredOps.value.find((op, index) => keyOf(op, index) === selectedKey.value)
  return found ? [found] : filteredOps.value
})

function handleNodeClick(data: TreeNode) {
  if (data.key === '__root__') {
    selectedKey.value = null
    return
  }
  selectedKey.value = data.key
}

watch(keyword, () => {
  selectedKey.value = null
})
</script>

<template>
  <div class="seq-panel">
    <div class="seq-left">
      <el-input v-model="keyword" size="small" clearable placeholder="搜索工序" class="seq-search" />
      <el-tree
        :data="treeData"
        node-key="key"
        default-expand-all
        highlight-current
        :expand-on-click-node="false"
        @node-click="handleNodeClick"
      />
    </div>
    <div class="seq-right">
      <el-table v-loading="loading" :data="tableData" stripe border size="small">
        <el-table-column
          v-for="col in columns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
        >
          <template v-if="col.formatter" #default="{ row }">{{ col.formatter(row) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.seq-panel {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.seq-left {
  flex: none;
  width: 240px;
  border-right: 1px solid var(--el-border-color-lighter);
  padding-right: 12px;
}
.seq-search {
  margin-bottom: 8px;
}
.seq-right {
  flex: 1;
  min-width: 0;
}
</style>
