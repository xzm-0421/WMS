<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMesPlan, getMesReports, getMesWorkCenterOptions, type MesOpPlan, type MesReport, type MesRouteOp } from '@/api/mes'
import { formatDate, formatDateTime } from '@/utils/format'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import DetailTabs from '@/components/detail/DetailTabs.vue'
import SequenceOpsPanel from '@/components/detail/SequenceOpsPanel.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const planId = Number(route.params.id)
const loading = ref(false)
const plan = ref<MesOpPlan | null>(null)
const routeOps = ref<MesRouteOp[]>([])
const reports = ref<MesReport[]>([])
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
  { prop: 'opNo', label: '工序号', width: 80, formatter: () => '-' },
  { prop: 'processCode', label: '工序编码', width: 120 },
  { prop: 'processName', label: '工序名称', minWidth: 140 },
  { prop: 'processDesc', label: '工序说明', minWidth: 140, formatter: () => '-' },
  { prop: 'opStatus', label: '工序状态', width: 100, formatter: () => '-' },
  { prop: 'unitCode', label: '工序单位', width: 90, formatter: () => '-' },
  { prop: 'opQty', label: '工序数量', width: 90, formatter: () => '-' },
  { prop: 'orgCode', label: '加工组织', width: 110, formatter: () => '-' },
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
]

const basicItems = computed(() => {
  const p = plan.value
  if (!p) return []
  return [
    { label: '单据编号', value: p.erpBillNo },
    { label: '单据类型', value: '工序计划' },
    { label: '单据状态', slot: 'erpStatus' },
    { label: '工单号', value: p.moNo },
    { label: '产品编码', value: p.productCode },
    { label: '产品名称', value: p.productName },
    { label: '物料内码', value: p.erpMaterialId },
    { label: '数量', value: p.planQty },
    { label: '已报工', value: p.reportedQty },
    { label: '返工已报', value: p.reworkReportedQty },
    { label: '生产车间', value: p.workShopName || p.workShopCode },
    { label: '工艺路线', value: routeOps.value.length ? `${routeOps.value.length} 道工序` : '-' },
    { label: '计划开始', value: formatDate(p.planStart) },
    { label: '计划完成', value: formatDate(p.planEnd) },
    { label: '超收比例', value: p.overReceiveRatio },
    { label: '同步状态', slot: 'syncStatus' },
    { label: '最后同步', value: formatDateTime(p.lastSyncTime) },
    { label: '变更拒绝原因', value: p.changeRejectReason, span: 2 },
  ]
})

async function load() {
  loading.value = true
  try {
    const vo = await getMesPlan(planId)
    plan.value = vo.plan
    routeOps.value = vo.routeOps || []
    if (plan.value?.moNo && plan.value?.processCode) {
      const res = await getMesReports({
        moNo: plan.value.moNo,
        processCode: plan.value.processCode,
        current: 1,
        size: 200,
      })
      reports.value = res.records || []
    }
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
    title="工序计划详情"
    :code="plan?.moNo || String(planId)"
    :status="plan?.planStatus"
    back-to="/mes/plans"
  >
    <BasicInfoGrid :items="basicItems">
      <template #erpStatus>
        <OrderStatusTag :status="plan?.planStatus" />
        <span v-if="plan?.erpStatus" class="erp-status">{{ plan.erpStatus }}</span>
      </template>
      <template #syncStatus>
        <OrderStatusTag :status="plan?.syncStatus" pending-label="未同步" failed-label="同步失败" />
      </template>
    </BasicInfoGrid>

    <template #tabs>
      <DetailTabs
        v-model="activeTab"
        :tabs="[
          { name: 'ops', label: '工序明细' },
          { name: 'reports', label: '报工记录' },
        ]"
      >
        <template #ops>
          <SequenceOpsPanel :ops="routeOps" :columns="opColumns" :loading="loading" />
        </template>
        <template #reports>
          <el-table v-loading="loading" :data="reports" stripe border size="small">
            <el-table-column prop="reportNo" label="报工单号" width="150" />
            <el-table-column prop="reportType" label="类型" width="80" />
            <el-table-column prop="qty" label="数量" width="90" />
            <el-table-column prop="weightKg" label="重量" width="90" />
            <el-table-column prop="operatorName" label="操作员" width="100" />
            <el-table-column label="报工时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.reportTime) }}</template>
            </el-table-column>
            <el-table-column label="同步状态" width="120">
              <template #default="{ row }">
                <OrderStatusTag :status="row.syncStatus" pending-label="待同步" failed-label="同步失败" />
              </template>
            </el-table-column>
            <el-table-column prop="erpBillNo" label="ERP单号" width="140" />
            <el-table-column prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip />
          </el-table>
        </template>
      </DetailTabs>
    </template>
  </DetailShell>
</template>

<style scoped>
.erp-status {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
</style>
