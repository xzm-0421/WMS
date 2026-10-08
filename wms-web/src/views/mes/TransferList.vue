<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getMesTransfers,
  retryMesTransfer,
  type MesTransfer,
} from '@/api/mes'
import { useUserStore } from '@/stores/user'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<MesTransfer[]>([])
const total = ref(0)
const query = reactive({ transferNo: '', moNo: '', syncStatus: '', current: 1, size: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await getMesTransfers(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleRetry(row: MesTransfer) {
  await retryMesTransfer(row.transferNo!)
  ElMessage.success('已加入重试队列')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="转移单号">
        <el-input v-model="query.transferNo" clearable />
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
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column label="转移单号" width="150">
        <template #default="{ row }">
          <RouterLink class="detail-link" :to="`/mes/transfers/${encodeURIComponent(row.transferNo)}`">
            {{ row.transferNo }}
          </RouterLink>
        </template>
      </el-table-column>
      <el-table-column prop="moNo" label="工单号" width="140" />
      <el-table-column label="源工序" width="130">
        <template #default="{ row }">{{ row.fromProcessName || row.fromProcessCode }}</template>
      </el-table-column>
      <el-table-column label="目标工序" width="130">
        <template #default="{ row }">{{ row.toProcessName || row.toProcessCode }}</template>
      </el-table-column>
      <el-table-column prop="qty" label="数量" width="90" />
      <el-table-column label="方式" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.autoFlag === 1" type="info" size="small">自动</el-tag>
          <el-tag v-else size="small">手工</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operatorName" label="操作员" width="90" />
      <el-table-column prop="transferTime" label="转移时间" width="170" />
      <el-table-column label="同步状态" width="120">
        <template #default="{ row }">
          <OrderStatusTag :status="row.syncStatus" pending-label="待同步" failed-label="同步失败" />
        </template>
      </el-table-column>
      <el-table-column prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="(row.syncStatus === 'FAILED' || row.syncStatus === 'MANUAL_REQUIRED')
              && userStore.hasPermission('mes:transfer:retry')"
            link
            type="warning"
            @click="handleRetry(row)"
          >
            手动重试
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
</template>

<style scoped>
.detail-link {
  color: var(--el-color-primary);
}
</style>
