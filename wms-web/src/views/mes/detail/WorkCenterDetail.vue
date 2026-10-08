<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMesWorkCenter, type MesWorkCenter } from '@/api/mes'
import { formatDateTime } from '@/utils/format'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const workCenterCode = String(route.params.workCenterCode)
const loading = ref(false)
const workCenter = ref<MesWorkCenter | null>(null)

const basicItems = computed(() => {
  const w = workCenter.value
  if (!w) return []
  return [
    { label: '工作中心编码', value: w.workCenterCode },
    { label: '名称', value: w.workCenterName },
    { label: '所属车间', value: w.workShopName || w.workShopCode },
    { label: '部门', value: w.deptName || w.deptCode },
    { label: '产能', value: w.capacity },
    { label: '工作日历', value: w.calendarName || w.calendarCode },
    { label: '内码', value: w.erpId },
    { label: '状态', slot: 'status' },
    { label: '同步状态', slot: 'syncStatus' },
    { label: '最后同步', value: formatDateTime(w.lastSyncTime) },
    { label: '失败原因', value: w.failReason, span: 2 },
  ]
})

async function load() {
  loading.value = true
  try {
    workCenter.value = await getMesWorkCenter(workCenterCode)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailShell
    title="工作中心详情"
    :code="workCenter?.workCenterCode || workCenterCode"
    :status="workCenter?.status"
    back-to="/mes/work-centers"
  >
    <div v-loading="loading">
      <BasicInfoGrid :items="basicItems">
        <template #status>
          <OrderStatusTag :status="workCenter?.status" />
        </template>
        <template #syncStatus>
          <OrderStatusTag :status="workCenter?.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </BasicInfoGrid>
    </div>
  </DetailShell>
</template>
