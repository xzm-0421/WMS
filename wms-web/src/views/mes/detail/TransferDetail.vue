<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMesTransfer, type MesTransfer } from '@/api/mes'
import { formatDateTime } from '@/utils/format'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import DetailTabs from '@/components/detail/DetailTabs.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const transferNo = String(route.params.transferNo)
const loading = ref(false)
const transfer = ref<MesTransfer | null>(null)
const activeTab = ref('related')

const basicItems = computed(() => {
  const t = transfer.value
  if (!t) return []
  return [
    { label: '转移单号', value: t.transferNo },
    { label: '工单号', value: t.moNo },
    { label: '源工序', value: `${t.fromProcessCode || ''} ${t.fromProcessName || ''}`.trim() },
    { label: '目标工序', value: `${t.toProcessCode || ''} ${t.toProcessName || ''}`.trim() },
    { label: '数量', value: t.qty },
    { label: '转移方式', value: t.autoFlag === 1 ? '自动转移' : '手工转移' },
    { label: '操作员', value: t.operatorName },
    { label: '转移时间', value: formatDateTime(t.transferTime) },
    { label: '同步状态', slot: 'syncStatus' },
    { label: '备注', value: t.remark, span: 2 },
  ]
})

const relatedItems = computed(() => {
  const t = transfer.value
  if (!t) return []
  return [
    { label: '同步状态', slot: 'syncStatus' },
    { label: '同步时间', value: formatDateTime(t.syncTime) },
    { label: '重试次数', value: t.retryCount },
    { label: '下次重试', value: formatDateTime(t.nextRetryTime) },
    { label: 'ERP单号', value: t.erpBillNo },
    { label: '取消原因', value: t.cancelReason },
    { label: '失败原因', value: t.failReason, span: 2 },
  ]
})

async function load() {
  loading.value = true
  try {
    transfer.value = await getMesTransfer(transferNo)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailShell
    title="工序转移详情"
    :code="transfer?.transferNo || transferNo"
    :status="transfer?.syncStatus"
    back-to="/mes/transfers"
  >
    <BasicInfoGrid :items="basicItems">
      <template #syncStatus>
        <OrderStatusTag :status="transfer?.syncStatus" pending-label="待同步" failed-label="同步失败" />
      </template>
    </BasicInfoGrid>

    <template #tabs>
      <DetailTabs v-model="activeTab" :tabs="[{ name: 'related', label: '关联信息' }]">
        <template #related>
          <BasicInfoGrid :items="relatedItems" :columns="2">
            <template #syncStatus>
              <OrderStatusTag :status="transfer?.syncStatus" pending-label="待同步" failed-label="同步失败" />
            </template>
          </BasicInfoGrid>
        </template>
      </DetailTabs>
    </template>
  </DetailShell>
</template>
