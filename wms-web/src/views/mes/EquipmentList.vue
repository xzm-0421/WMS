<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMesEquipment, getMesEquipmentDetail, refreshMesEquipment, type MesEquipment } from '@/api/mes'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<MesEquipment[]>([])
const total = ref(0)
const query = reactive({
  equipmentCode: '',
  equipmentName: '',
  processCode: '',
  status: undefined as number | undefined,
  current: 1,
  size: 20,
})
const detailVisible = ref(false)
const current = ref<MesEquipment | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await getMesEquipment(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  await ElMessageBox.confirm(
    '将从金蝶ERP拉取设备主数据并写入本地，本地仅查询不维护。是否继续？',
    '从金蝶同步设备',
  )
  syncing.value = true
  try {
    const result = await refreshMesEquipment()
    ElMessage[result.success ? 'success' : 'warning'](result.message || '同步完成')
    await loadData()
  } finally {
    syncing.value = false
  }
}

async function showDetail(row: MesEquipment) {
  current.value = row.equipmentCode ? await getMesEquipmentDetail(row.equipmentCode) : row
  detailVisible.value = true
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="设备以金蝶ERP为权威源，同步后存储在本系统，页面只读不可改。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="设备编码">
        <el-input v-model="query.equipmentCode" clearable />
      </el-form-item>
      <el-form-item label="设备名称">
        <el-input v-model="query.equipmentName" clearable />
      </el-form-item>
      <el-form-item label="所属工序">
        <el-input v-model="query.processCode" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
          <el-option :value="1" label="启用" />
          <el-option :value="0" label="停用" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('mes:equipment:sync') || userStore.hasPermission('mes:master:sync')"
          type="success"
          :loading="syncing"
          @click="handleSync"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="equipmentCode" label="设备编码" width="140" />
      <el-table-column prop="equipmentName" label="设备名称" min-width="180" />
      <el-table-column prop="processCode" label="所属工序" width="120" />
      <el-table-column prop="specModel" label="规格型号" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><WmsStatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="同步" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </el-table-column>
      <el-table-column prop="lastSyncTime" label="最后同步" width="170" />
      <el-table-column prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip />
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

  <el-dialog v-model="detailVisible" title="设备详情" width="520px">
    <el-descriptions v-if="current" :column="1" border>
      <el-descriptions-item label="设备编码">{{ current.equipmentCode }}</el-descriptions-item>
      <el-descriptions-item label="设备名称">{{ current.equipmentName }}</el-descriptions-item>
      <el-descriptions-item label="所属工序">{{ current.processCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="规格型号">{{ current.specModel || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ current.status === 1 ? '启用' : '停用' }}</el-descriptions-item>
      <el-descriptions-item label="同步状态">{{ current.syncStatus || '-' }}</el-descriptions-item>
      <el-descriptions-item label="最后同步">{{ current.lastSyncTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="失败原因">{{ current.failReason || '-' }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>
