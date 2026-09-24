<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMesPlan, getMesPlans, refreshMesPlans, retryMesPlan, type MesOpPlan, type MesRouteOp } from '@/api/mes'

const loading = ref(false)
const tableData = ref<MesOpPlan[]>([])
const total = ref(0)
const query = reactive({
  moNo: '',
  productCode: '',
  status: '',
  current: 1,
  size: 20,
})
const drawerVisible = ref(false)
const current = ref<MesOpPlan | null>(null)
const routeOps = ref<MesRouteOp[]>([])

async function loadData() {
  loading.value = true
  try {
    const res = await getMesPlans(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function showDetail(row: MesOpPlan) {
  const vo = await getMesPlan(row.id!)
  current.value = vo.plan
  routeOps.value = vo.routeOps || []
  drawerVisible.value = true
}

async function handleRefresh() {
  const result = await refreshMesPlans(query.moNo || undefined)
  ElMessage[result.success ? 'success' : 'warning'](result.message || '刷新完成')
  loadData()
}

async function handleRetry(row: MesOpPlan) {
  const result = await retryMesPlan(row.id!)
  ElMessage[result.success ? 'success' : 'warning'](result.message || '重试完成')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="工单号">
        <el-input v-model="query.moNo" clearable />
      </el-form-item>
      <el-form-item label="产品编码">
        <el-input v-model="query.productCode" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <WmsSelect v-model="query.status" clearable placeholder="全部">
          <el-option label="已下达" value="RELEASED" />
          <el-option label="生产中" value="RUNNING" />
          <el-option label="已暂停" value="PAUSED" />
          <el-option label="已关闭" value="CLOSED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="handleRefresh">刷新列表</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="moNo" label="工单号" width="150" />
      <el-table-column prop="productCode" label="产品编码" width="130" />
      <el-table-column prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="processCode" label="工序编码" width="110" />
      <el-table-column prop="processName" label="工序名称" width="120" />
      <el-table-column prop="workShopName" label="车间" width="120" show-overflow-tooltip />
      <el-table-column prop="planQty" label="计划数量" width="100" />
      <el-table-column prop="reportedQty" label="已报工" width="90" />
      <el-table-column label="计划开始" width="120">
        <template #default="{ row }"><WmsDateText :value="row.planStart" /></template>
      </el-table-column>
      <el-table-column label="计划完成" width="120">
        <template #default="{ row }"><WmsDateText :value="row.planEnd" /></template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><OrderStatusTag :status="row.planStatus" /></template>
      </el-table-column>
      <el-table-column label="MES同步" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </el-table-column>
      <el-table-column prop="lastSyncTime" label="最后同步时间" width="170">
        <template #default="{ row }">
          <WmsDateText :value="row.lastSyncTime" />
        </template>
      </el-table-column>
      <el-table-column prop="changeRejectReason" label="变更拒绝原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.changeRejectReason" style="color: var(--el-color-danger)">{{ row.changeRejectReason }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">查看详情</el-button>
          <el-button
            v-if="row.syncStatus === 'FAILED'"
            link
            type="warning"
            @click="handleRetry(row)"
          >
            手动重试同步
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

  <el-drawer v-model="drawerVisible" title="工序计划详情" size="520px">
    <el-descriptions v-if="current" :column="1" border>
      <el-descriptions-item label="工单号">{{ current.moNo }}</el-descriptions-item>
      <el-descriptions-item label="产品">{{ current.productCode }} {{ current.productName }}</el-descriptions-item>
      <el-descriptions-item label="工序">{{ current.processCode }} {{ current.processName }}</el-descriptions-item>
      <el-descriptions-item label="车间">{{ current.workShopName || current.workShopCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="计划/已报">{{ current.planQty }} / {{ current.reportedQty }}</el-descriptions-item>
      <el-descriptions-item label="ERP单号">{{ current.erpBillNo || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="current.changeRejectReason" label="变更拒绝">
        {{ current.changeRejectReason }}
      </el-descriptions-item>
    </el-descriptions>
    <h4 style="margin: 16px 0 8px">工艺路线</h4>
    <el-table :data="routeOps" stripe>
      <el-table-column prop="seqNo" label="#" width="50" />
      <el-table-column prop="processCode" label="工序" width="110" />
      <el-table-column prop="processName" label="名称" />
    </el-table>
  </el-drawer>
</template>
