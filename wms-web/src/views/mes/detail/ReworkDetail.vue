<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMesReworkSequence, type MesDefect, type MesReworkOp } from '@/api/mes'
import { getStatusLabel } from '@/utils/status'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import DetailTabs from '@/components/detail/DetailTabs.vue'
import SequenceOpsPanel from '@/components/detail/SequenceOpsPanel.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const defectNo = String(route.params.defectNo)
const loading = ref(false)
const defect = ref<MesDefect | null>(null)
const operations = ref<MesReworkOp[]>([])
const activeTab = ref('seq')

const opColumns = [
  { prop: 'seqNo', label: '工序序列', width: 90 },
  { prop: 'processCode', label: '工序编码', width: 130 },
  { prop: 'processName', label: '工序名称', minWidth: 150 },
  { prop: 'planQty', label: '计划数量', width: 100 },
  { prop: 'reportedQty', label: '已报数量', width: 100 },
  {
    prop: 'opStatus',
    label: '工序状态',
    width: 110,
    formatter: (row: Record<string, unknown>) => getStatusLabel(row.opStatus as string | undefined),
  },
]

const basicItems = computed(() => {
  const d = defect.value
  if (!d) return []
  return [
    { label: '不良单号', value: d.defectNo },
    { label: '工单号', value: d.moNo },
    { label: '源工序', value: d.sourceProcessName || d.sourceProcessCode },
    { label: '不良数量', value: d.defectQty },
    { label: '不良类型', value: d.defectType },
    { label: '责任人', value: d.ownerName },
    { label: '返工状态', slot: 'reworkStatus' },
    { label: '不良单同步', slot: 'defectSync' },
    { label: '返工单同步', slot: 'reworkSync' },
    { label: '不良描述', value: d.defectDesc, span: 2 },
  ]
})

async function load() {
  loading.value = true
  try {
    const vo = await getMesReworkSequence(defectNo)
    defect.value = vo.defect || null
    operations.value = vo.operations || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailShell
    title="不良 / 返工详情"
    :code="defect?.defectNo || defectNo"
    :status="defect?.reworkStatus"
    back-to="/mes/rework"
  >
    <BasicInfoGrid :items="basicItems">
      <template #reworkStatus>
        <OrderStatusTag :status="defect?.reworkStatus" />
      </template>
      <template #defectSync>
        <OrderStatusTag :status="defect?.defectSyncStatus" pending-label="待同步" failed-label="同步失败" />
      </template>
      <template #reworkSync>
        <OrderStatusTag :status="defect?.reworkSyncStatus" pending-label="待同步" failed-label="同步失败" />
      </template>
    </BasicInfoGrid>

    <template #tabs>
      <DetailTabs v-model="activeTab" :tabs="[{ name: 'seq', label: '返工序列' }]">
        <template #seq>
          <SequenceOpsPanel :ops="operations" :columns="opColumns" :loading="loading" />
        </template>
      </DetailTabs>
    </template>
  </DetailShell>
</template>
