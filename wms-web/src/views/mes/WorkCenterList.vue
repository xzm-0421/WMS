<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMesWorkCenters, refreshMesWorkCenters, type MesWorkCenter } from '@/api/mes'
import { useUserStore } from '@/stores/user'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<MesWorkCenter[]>([])
const total = ref(0)
const query = reactive({
  workCenterCode: '',
  workCenterName: '',
  status: undefined as number | undefined,
  current: 1,
  size: 20,
})

async function loadData() {
  loading.value = true
  try {
    const res = await getMesWorkCenters(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  await ElMessageBox.confirm('将从金蝶ERP拉取工作中心到本地，是否继续？', '从金蝶同步工作中心')
  syncing.value = true
  try {
    const result = await refreshMesWorkCenters()
    ElMessage[result.success ? 'success' : 'warning'](result.message || '同步完成')
    await loadData()
  } finally {
    syncing.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="工作中心以金蝶ERP为权威源，同步后存储在本系统，页面只读不可改。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="工作中心编码">
        <el-input v-model="query.workCenterCode" clearable />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="query.workCenterName" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('mes:workcenter:sync') || userStore.hasPermission('mes:master:sync')"
          type="success"
          :loading="syncing"
          @click="handleSync"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column label="工作中心编码" width="150">
        <template #default="{ row }">
          <RouterLink class="detail-link" :to="`/mes/work-centers/${encodeURIComponent(row.workCenterCode)}`">
            {{ row.workCenterCode }}
          </RouterLink>
        </template>
      </el-table-column>
      <el-table-column prop="workCenterName" label="名称" min-width="160" />
      <el-table-column prop="workShopName" label="所属车间" width="140" show-overflow-tooltip />
      <el-table-column prop="deptName" label="部门" width="140" show-overflow-tooltip />
      <el-table-column prop="capacity" label="产能" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><WmsStatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="同步" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </el-table-column>
      <el-table-column prop="lastSyncTime" label="最后同步" width="170" />
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
</template>

<style scoped>
.detail-link {
  color: var(--el-color-primary);
}
</style>
