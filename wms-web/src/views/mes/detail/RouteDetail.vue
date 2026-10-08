<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMesRoute, getMesWorkCenterOptions, type MesRoute, type MesRouteOp } from '@/api/mes'
import { formatDateTime } from '@/utils/format'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import DetailTabs from '@/components/detail/DetailTabs.vue'
import SequenceOpsPanel from '@/components/detail/SequenceOpsPanel.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const routeId = Number(route.params.id)
const loading = ref(false)
const header = ref<MesRoute | null>(null)
const operations = ref<MesRouteOp[]>([])
const wcMap = ref<Record<string, string>>({})
const activeTab = ref('ops')

async function loadWcMap() {
  try {
    const list = await getMesWorkCenterOptions()
    wcMap.value = Object.fromEntries(
      list.map((w) => [w.workCenterCode || '', w.workCenterName || '']),
    )
  } catch {
    wcMap.value = {}
  }
}

const opColumns = [
  { prop: 'seqNo', label: '工序序列', width: 90 },
  { prop: 'processCode', label: '工序编码', width: 130 },
  { prop: 'processName', label: '工序名称', minWidth: 150 },
  {
    prop: 'workCenterCode',
    label: '工作中心',
    width: 170,
    formatter: (row: Record<string, unknown>) => {
      const code = row.workCenterCode as string | undefined
      if (!code) return '-'
      const name = wcMap.value[code]
      return name ? `${code} ${name}` : code
    },
  },
  { prop: 'stdHours', label: '标准工时', width: 100 },
  { prop: 'inspectFlag', label: '质检', width: 80, formatter: (row: Record<string, unknown>) => (row.inspectFlag === 1 ? '是' : '否') },
  { prop: 'reworkJoinFlag', label: '返工汇合', width: 100, formatter: (row: Record<string, unknown>) => (row.reworkJoinFlag === 1 ? '是' : '否') },
  { prop: 'isConvergeOp', label: '汇合工序', width: 100, formatter: (row: Record<string, unknown>) => (row.isConvergeOp ? '是' : '否') },
]

const basicItems = computed(() => {
  const r = header.value
  if (!r) return []
  return [
    { label: '路线编码', value: r.routeCode },
    { label: '路线名称', value: r.routeName },
    { label: '物料编码', value: r.productCode },
    { label: '产品名称', value: r.productName },
    { label: '物料内码', value: r.erpMaterialId },
    { label: '版本号', value: r.versionNo },
    { label: '超收比例', value: r.overReceiveRatio },
    { label: '状态', slot: 'status' },
    { label: '同步状态', slot: 'syncStatus' },
    { label: '最后同步', value: formatDateTime(r.lastSyncTime) },
    { label: '失败原因', value: r.failReason, span: 2 },
  ]
})

async function load() {
  loading.value = true
  try {
    const vo = await getMesRoute(routeId)
    header.value = vo.header
    operations.value = vo.operations || []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadWcMap()
  load()
})
</script>

<template>
  <DetailShell
    title="工艺路线详情"
    :code="header?.routeCode || String(routeId)"
    :status="header?.status"
    back-to="/mes/routes"
  >
    <BasicInfoGrid :items="basicItems">
      <template #status>
        <OrderStatusTag :status="header?.status" />
      </template>
      <template #syncStatus>
        <OrderStatusTag :status="header?.syncStatus" pending-label="未同步" failed-label="同步失败" />
      </template>
    </BasicInfoGrid>

    <template #tabs>
      <DetailTabs v-model="activeTab" :tabs="[{ name: 'ops', label: '工序明细' }]">
        <template #ops>
          <SequenceOpsPanel :ops="operations" :columns="opColumns" :loading="loading" />
        </template>
      </DetailTabs>
    </template>
  </DetailShell>
</template>
