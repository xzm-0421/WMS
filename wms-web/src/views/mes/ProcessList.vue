<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMesProcesses, refreshMesProcesses, type MesProcess } from '@/api/mes'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<MesProcess[]>([])
const total = ref(0)
const query = reactive({ processCode: '', processName: '', status: undefined as number | undefined, current: 1, size: 20 })
const detailVisible = ref(false)
const current = ref<MesProcess | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await getMesProcesses(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  await ElMessageBox.confirm('将从金蝶ERP拉取工序到本地，是否继续？', '从金蝶同步工序')
  syncing.value = true
  try {
    const result = await refreshMesProcesses()
    ElMessage[result.success ? 'success' : 'warning'](result.message || '同步完成')
    await loadData()
  } finally {
    syncing.value = false
  }
}

function showDetail(row: MesProcess) {
  current.value = row
  detailVisible.value = true
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="工序以金蝶ERP为权威源，同步后存储在本系统，页面只读不可改。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="工序编码">
        <el-input v-model="query.processCode" clearable />
      </el-form-item>
      <el-form-item label="工序名称">
        <el-input v-model="query.processName" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('mes:process:sync') || userStore.hasPermission('mes:master:sync')"
          type="success"
          :loading="syncing"
          @click="handleSync"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="processCode" label="工序编码" width="130" />
      <el-table-column prop="processName" label="工序名称" min-width="160" />
      <el-table-column prop="deptName" label="所属部门" width="140" />
      <el-table-column label="报工" width="70">
        <template #default="{ row }">{{ row.reportFlag === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="转移" width="70">
        <template #default="{ row }">{{ row.transferFlag === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="质检" width="70">
        <template #default="{ row }">{{ row.inspectFlag === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><WmsStatusTag :status="row.status" /></template>
      </el-table-column>
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

  <el-dialog v-model="detailVisible" title="工序详情" width="520px">
    <el-descriptions v-if="current" :column="1" border>
      <el-descriptions-item label="工序编码">{{ current.processCode }}</el-descriptions-item>
      <el-descriptions-item label="工序名称">{{ current.processName }}</el-descriptions-item>
      <el-descriptions-item label="所属部门">{{ current.deptName }} ({{ current.deptCode || '-' }})</el-descriptions-item>
      <el-descriptions-item label="报工工序">{{ current.reportFlag === 1 ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="转移工序">{{ current.transferFlag === 1 ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="质检工序">{{ current.inspectFlag === 1 ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="超收比例">{{ current.overReceiveRatio ?? '-' }}</el-descriptions-item>
      <el-descriptions-item label="最后同步">{{ current.lastSyncTime || '-' }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>
