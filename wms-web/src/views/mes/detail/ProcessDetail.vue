<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMesProcessDetail, type MesProcess } from '@/api/mes'
import { formatDateTime } from '@/utils/format'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const processCode = String(route.params.processCode)
const loading = ref(false)
const process = ref<MesProcess | null>(null)

function yesNo(value?: number | boolean | null): string {
  return value === 1 || value === true ? '是' : '否'
}

const basicItems = computed(() => {
  const p = process.value
  if (!p) return []
  return [
    { label: '工序编码', value: p.processCode },
    { label: '工序名称', value: p.processName },
    { label: '所属部门', value: p.deptName },
    { label: '部门编码', value: p.deptCode },
    { label: '报工工序', value: yesNo(p.reportFlag) },
    { label: '转移工序', value: yesNo(p.transferFlag) },
    { label: '质检工序', value: yesNo(p.inspectFlag) },
    { label: '汇合工序', value: yesNo(p.isConvergeOp) },
    { label: '超收比例', value: p.overReceiveRatio },
    { label: '状态', slot: 'status' },
    { label: '同步状态', slot: 'syncStatus' },
    { label: '最后同步', value: formatDateTime(p.lastSyncTime) },
    { label: '失败原因', value: p.failReason, span: 2 },
  ]
})

async function load() {
  loading.value = true
  try {
    process.value = await getMesProcessDetail(processCode)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailShell
    title="工序详情"
    :code="process?.processCode || processCode"
    :status="process?.status"
    back-to="/mes/process"
  >
    <div v-loading="loading">
      <BasicInfoGrid :items="basicItems">
        <template #status>
          <OrderStatusTag :status="process?.status" />
        </template>
        <template #syncStatus>
          <OrderStatusTag :status="process?.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </BasicInfoGrid>
    </div>
  </DetailShell>
</template>
