<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMesRoute, getMesRoutes, refreshMesRoutes, type MesRoute, type MesRouteOp } from '@/api/mes'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<MesRoute[]>([])
const total = ref(0)
const query = reactive({ productCode: '', productName: '', current: 1, size: 20 })
const detailVisible = ref(false)
const current = ref<MesRoute | null>(null)
const routeOps = ref<MesRouteOp[]>([])

async function loadData() {
  loading.value = true
  try {
    const res = await getMesRoutes(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  await ElMessageBox.confirm('将从金蝶ERP拉取工艺路线到本地，是否继续？', '从金蝶同步工艺路线')
  syncing.value = true
  try {
    const result = await refreshMesRoutes()
    ElMessage[result.success ? 'success' : 'warning'](result.message || '同步完成')
    await loadData()
  } finally {
    syncing.value = false
  }
}

async function showDetail(row: MesRoute) {
  const vo = await getMesRoute(row.id!)
  current.value = vo.header
  routeOps.value = vo.operations || []
  detailVisible.value = true
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="工艺路线以金蝶ERP为权威源，同步后存储在本系统，页面只读不可改。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="产品编码">
        <el-input v-model="query.productCode" clearable />
      </el-form-item>
      <el-form-item label="产品名称">
        <el-input v-model="query.productName" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('mes:route:sync') || userStore.hasPermission('mes:master:sync')"
          type="success"
          :loading="syncing"
          @click="handleSync"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="routeCode" label="路线编码" width="140" />
      <el-table-column prop="routeName" label="路线名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="productCode" label="物料编码" width="140" />
      <el-table-column prop="productName" label="产品名称" min-width="180" />
      <el-table-column prop="versionNo" label="版本号" width="100" />
      <el-table-column label="同步" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </el-table-column>
      <el-table-column prop="lastSyncTime" label="最后同步" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">查看详情</el-button>
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

  <el-dialog v-model="detailVisible" title="工艺路线详情" width="680px">
    <el-descriptions v-if="current" :column="1" border>
      <el-descriptions-item label="路线编码">{{ current.routeCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="路线名称">{{ current.routeName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="物料编码">{{ current.productCode }}</el-descriptions-item>
      <el-descriptions-item label="产品名称">{{ current.productName }}</el-descriptions-item>
      <el-descriptions-item label="版本号">{{ current.versionNo }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="routeOps" stripe style="margin-top: 12px">
      <el-table-column prop="seqNo" label="顺序" width="70" />
      <el-table-column prop="processCode" label="工序编码" width="120" />
      <el-table-column prop="processName" label="工序名称" />
      <el-table-column prop="workCenterCode" label="工作中心" width="120" />
      <el-table-column prop="stdHours" label="标准工时" width="100" />
      <el-table-column label="质检" width="70">
        <template #default="{ row }">{{ row.inspectFlag === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="返工汇合" width="90">
        <template #default="{ row }">{{ row.reworkJoinFlag === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="汇合工序" width="90">
        <template #default="{ row }">{{ row.isConvergeOp ? '是' : '否' }}</template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>
