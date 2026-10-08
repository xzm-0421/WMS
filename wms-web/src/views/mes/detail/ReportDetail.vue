<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMesReport, getMesReports, type MesReport } from '@/api/mes'
import { formatDateTime } from '@/utils/format'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import DetailTabs from '@/components/detail/DetailTabs.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const reportNo = String(route.params.reportNo)
const loading = ref(false)
const report = ref<MesReport | null>(null)
const siblings = ref<MesReport[]>([])
const activeTab = ref('list')

const basicItems = computed(() => {
  const r = report.value
  if (!r) return []
  return [
    { label: '报工单号', value: r.reportNo },
    { label: '工单号', value: r.moNo },
    { label: '工序编码', value: r.processCode },
    { label: '工序名称', value: r.processName },
    { label: '报工类型', value: r.reportType },
    { label: '数量', value: r.qty },
    { label: '重量(kg)', value: r.weightKg },
    { label: '设备', value: r.equipmentName },
    { label: '操作员', value: r.operatorName },
    { label: '报工时间', value: formatDateTime(r.reportTime) },
    { label: '关联不良单', value: r.defectNo },
    { label: '同步状态', slot: 'syncStatus' },
    { label: '备注', value: r.remark, span: 2 },
  ]
})

const syncItems = computed(() => {
  const r = report.value
  if (!r) return []
  return [
    { label: '同步状态', slot: 'syncStatus' },
    { label: '同步时间', value: formatDateTime(r.syncTime) },
    { label: '重试次数', value: r.retryCount },
    { label: '下次重试', value: formatDateTime(r.nextRetryTime) },
    { label: 'ERP单号', value: r.erpBillNo },
    { label: '客户端单号', value: r.clientReportNo },
    { label: '客户端时间', value: formatDateTime(r.clientTime) },
    { label: '取消原因', value: r.cancelReason },
    { label: '失败原因', value: r.failReason, span: 2 },
  ]
})

async function load() {
  loading.value = true
  try {
    report.value = await getMesReport(reportNo)
    if (report.value.moNo && report.value.processCode) {
      const res = await getMesReports({
        moNo: report.value.moNo,
        processCode: report.value.processCode,
        current: 1,
        size: 200,
      })
      siblings.value = res.records || []
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailShell
    title="报工记录详情"
    :code="report?.reportNo || reportNo"
    :status="report?.syncStatus"
    back-to="/mes/reports"
  >
    <BasicInfoGrid :items="basicItems">
      <template #syncStatus>
        <OrderStatusTag :status="report?.syncStatus" pending-label="待同步" failed-label="同步失败" />
      </template>
    </BasicInfoGrid>

    <template #tabs>
      <DetailTabs
        v-model="activeTab"
        :tabs="[
          { name: 'list', label: '报工明细' },
          { name: 'sync', label: '同步信息' },
        ]"
      >
        <template #list>
          <el-table v-loading="loading" :data="siblings" stripe border size="small">
            <el-table-column prop="reportNo" label="报工单号" width="150" />
            <el-table-column prop="reportType" label="类型" width="80" />
            <el-table-column prop="qty" label="数量" width="90" />
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
        <template #sync>
          <BasicInfoGrid :items="syncItems" :columns="2">
            <template #syncStatus>
              <OrderStatusTag :status="report?.syncStatus" pending-label="待同步" failed-label="同步失败" />
            </template>
          </BasicInfoGrid>
        </template>
      </DetailTabs>
    </template>
  </DetailShell>
</template>
