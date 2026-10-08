<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMesResource, RESOURCE_TYPE_LABEL, type MesResource } from '@/api/mes'
import { formatDateTime } from '@/utils/format'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const resourceCode = String(route.params.resourceCode)
const loading = ref(false)
const resource = ref<MesResource | null>(null)

const basicItems = computed(() => {
  const r = resource.value
  if (!r) return []
  return [
    { label: '资源编码', value: r.resourceCode },
    { label: '名称', value: r.resourceName },
    { label: '资源类别', value: RESOURCE_TYPE_LABEL[r.resourceType || ''] || r.resourceTypeCode },
    { label: '类别原值', value: r.resourceTypeCode },
    { label: '工作中心', value: r.workCenterName || r.workCenterCode },
    { label: '产能', value: r.capacity },
    { label: '单位', value: r.unitCode },
    { label: '关联对象', value: r.refName || r.refCode },
    { label: '内码', value: r.erpId },
    { label: '状态', slot: 'status' },
    { label: '同步状态', slot: 'syncStatus' },
    { label: '最后同步', value: formatDateTime(r.lastSyncTime) },
    { label: '失败原因', value: r.failReason, span: 2 },
  ]
})

async function load() {
  loading.value = true
  try {
    resource.value = await getMesResource(resourceCode)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailShell
    title="资源详情"
    :code="resource?.resourceCode || resourceCode"
    :status="resource?.status"
    back-to="/mes/resources"
  >
    <div v-loading="loading">
      <BasicInfoGrid :items="basicItems">
        <template #status>
          <OrderStatusTag :status="resource?.status" />
        </template>
        <template #syncStatus>
          <OrderStatusTag :status="resource?.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </BasicInfoGrid>
    </div>
  </DetailShell>
</template>
