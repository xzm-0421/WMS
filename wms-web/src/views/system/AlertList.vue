<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ackAlert, closeAlert, getAlerts, type SysAlert } from '@/api/system'
import { useUserStore } from '@/stores/user'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<SysAlert[]>([])
const total = ref(0)
const query = reactive({ alertType: '', status: '', current: 1, size: 20 })

const TYPE_LABEL: Record<string, string> = {
  QUEUE_FULL: '同步队列满',
  SLA_TIMEOUT: 'SLA 超时',
  LONG_UNSYNCED: '长期未同步',
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAlerts(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleAck(row: SysAlert) {
  await ackAlert(row.id!)
  ElMessage.success('已确认')
  loadData()
}

async function handleClose(row: SysAlert) {
  await ElMessageBox.confirm('确认关闭该告警？', '关闭告警')
  await closeAlert(row.id!)
  ElMessage.success('已关闭')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="类型">
        <WmsSelect v-model="query.alertType" clearable placeholder="全部">
          <el-option label="同步队列满" value="QUEUE_FULL" />
          <el-option label="SLA 超时" value="SLA_TIMEOUT" />
          <el-option label="长期未同步" value="LONG_UNSYNCED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="状态">
        <WmsSelect v-model="query.status" clearable placeholder="全部">
          <el-option label="未处理" value="OPEN" />
          <el-option label="已确认" value="ACK" />
          <el-option label="已关闭" value="CLOSED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column label="级别" width="80">
        <template #default="{ row }">
          <el-tag :type="row.level === 'ERROR' ? 'danger' : 'warning'" size="small">{{ row.level }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="130">
        <template #default="{ row }">{{ TYPE_LABEL[row.alertType] || row.alertType }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
      <el-table-column prop="alertCount" label="次数" width="80" />
      <el-table-column label="最近发生" width="170">
        <template #default="{ row }"><WmsDateText :value="row.lastTime" /></template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'OPEN'" type="danger" size="small">未处理</el-tag>
          <el-tag v-else-if="row.status === 'ACK'" type="warning" size="small">已确认</el-tag>
          <OrderStatusTag v-else status="CLOSED" />
        </template>
      </el-table-column>
      <el-table-column prop="ackBy" label="处理人" width="110" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'OPEN' && userStore.hasPermission('system:alert:handle')"
            link
            type="primary"
            @click="handleAck(row)"
          >
            确认
          </el-button>
          <el-button
            v-if="row.status !== 'CLOSED' && userStore.hasPermission('system:alert:handle')"
            link
            type="danger"
            @click="handleClose(row)"
          >
            关闭
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
