<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMesEquipmentDetail, type MesEquipment } from '@/api/mes'
import { formatDateTime } from '@/utils/format'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const equipmentCode = String(route.params.equipmentCode)
const loading = ref(false)
const equipment = ref<MesEquipment | null>(null)

const basicItems = computed(() => {
  const e = equipment.value
  if (!e) return []
  return [
    { label: '设备编码', value: e.equipmentCode },
    { label: '设备名称', value: e.equipmentName },
    { label: '所属工序', value: e.processCode },
    { label: '规格型号', value: e.specModel },
    { label: '状态', slot: 'status' },
    { label: '同步状态', slot: 'syncStatus' },
    { label: '最后同步', value: formatDateTime(e.lastSyncTime) },
    { label: '失败原因', value: e.failReason, span: 2 },
  ]
})

async function load() {
  loading.value = true
  try {
    equipment.value = await getMesEquipmentDetail(equipmentCode)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailShell
    title="设备详情"
    :code="equipment?.equipmentCode || equipmentCode"
    :status="equipment?.status"
    back-to="/mes/equipment"
  >
    <div v-loading="loading">
      <BasicInfoGrid :items="basicItems">
        <template #status>
          <OrderStatusTag :status="equipment?.status" />
        </template>
        <template #syncStatus>
          <OrderStatusTag :status="equipment?.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </BasicInfoGrid>
    </div>
  </DetailShell>
</template>
