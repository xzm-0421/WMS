<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelMesReport,
  exportMesReports,
  getMesReport,
  getMesReports,
  retryMesReport,
  type MesReport,
} from '@/api/mes'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<MesReport[]>([])
const total = ref(0)
const query = reactive({ reportNo: '', moNo: '', syncStatus: '', current: 1, size: 20 })
const drawerVisible = ref(false)
const current = ref<MesReport | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await getMesReports(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function showDetail(row: MesReport) {
  current.value = await getMesReport(row.reportNo!)
  drawerVisible.value = true
}

async function handleRetry(row: MesReport) {
  await retryMesReport(row.reportNo!)
  ElMessage.success('已加入重试队列')
  loadData()
}

async function handleCancel(row: MesReport) {
  const { value } = await ElMessageBox.prompt('请输入取消原因', '取消暂存', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '原因',
  })
  await cancelMesReport(row.reportNo!, value)
  ElMessage.success('已取消暂存')
  loadData()
}

async function handleExport() {
  await exportMesReports({
    reportNo: query.reportNo || undefined,
    moNo: query.moNo || undefined,
    syncStatus: query.syncStatus || undefined,
  })
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="报工单号">
        <el-input v-model="query.reportNo" clearable />
      </el-form-item>
      <el-form-item label="工单号">
        <el-input v-model="query.moNo" clearable />
      </el-form-item>
      <el-form-item label="同步状态">
        <WmsSelect v-model="query.syncStatus" clearable placeholder="全部">
          <el-option label="待同步" value="PENDING" />
          <el-option label="同步中" value="SYNCING" />
          <el-option label="已同步" value="SUCCESS" />
          <el-option label="同步失败" value="FAILED" />
          <el-option label="待人工介入" value="MANUAL_REQUIRED" />
          <el-option label="已取消" value="CANCELLED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button v-if="userStore.hasPermission('mes:report:export')" @click="handleExport">导出</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="reportNo" label="报工单号" width="150" />
      <el-table-column prop="moNo" label="工单号" width="140" />
      <el-table-column prop="processName" label="工序" width="120" />
      <el-table-column prop="qty" label="数量" width="80" />
      <el-table-column prop="operatorName" label="操作员" width="90" />
      <el-table-column prop="reportTime" label="报工时间" width="170" />
      <el-table-column label="同步状态" width="120">
        <template #default="{ row }">
          <OrderStatusTag :status="row.syncStatus" pending-label="待同步" failed-label="同步失败" />
        </template>
      </el-table-column>
      <el-table-column prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">查看详情</el-button>
          <el-button
            v-if="row.syncStatus === 'FAILED' || row.syncStatus === 'MANUAL_REQUIRED'"
            link
            type="warning"
            @click="handleRetry(row)"
          >
            手动重试
          </el-button>
          <el-button
            v-if="row.syncStatus === 'PENDING' || row.syncStatus === 'FAILED'"
            link
            type="danger"
            @click="handleCancel(row)"
          >
            取消暂存
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px"
      @current-change="loadData"
    />
  </el-card>

  <el-drawer v-model="drawerVisible" title="报工详情" size="480px">
    <el-descriptions v-if="current" :column="1" border>
      <el-descriptions-item label="报工单号">{{ current.reportNo }}</el-descriptions-item>
      <el-descriptions-item label="工单号">{{ current.moNo }}</el-descriptions-item>
      <el-descriptions-item label="工序">{{ current.processCode }} {{ current.processName }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ current.reportType }}</el-descriptions-item>
      <el-descriptions-item label="数量">{{ current.qty }}</el-descriptions-item>
      <el-descriptions-item label="重量">{{ current.weightKg || '-' }}</el-descriptions-item>
      <el-descriptions-item label="设备">{{ current.equipmentName }}</el-descriptions-item>
      <el-descriptions-item label="操作员">{{ current.operatorName }}</el-descriptions-item>
      <el-descriptions-item label="ERP单号">{{ current.erpBillNo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="失败原因">{{ current.failReason || '-' }}</el-descriptions-item>
    </el-descriptions>
  </el-drawer>
</template>
