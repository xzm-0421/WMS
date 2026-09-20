<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getMesReports,
  getMesSyncPanel,
  retryMesReport,
  type MesReport,
  type MesSyncPanel,
} from '@/api/mes'

const panel = ref<MesSyncPanel | null>(null)
const failed = ref<MesReport[]>([])
let timer: number | undefined

async function load() {
  panel.value = await getMesSyncPanel()
  const res = await getMesReports({ syncStatus: 'FAILED', current: 1, size: 20 })
  const manual = await getMesReports({ syncStatus: 'MANUAL_REQUIRED', current: 1, size: 20 })
  failed.value = [...res.records, ...manual.records]
}

async function handleRetry(row: MesReport) {
  await retryMesReport(row.reportNo!)
  ElMessage.success('已加入重试队列')
  load()
}

onMounted(() => {
  load()
  timer = window.setInterval(load, 15000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">队列总数</div>
          <div class="stat-value">{{ panel?.queueTotal ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">待同步</div>
          <div class="stat-value">{{ panel?.pendingCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">同步中</div>
          <div class="stat-value">{{ panel?.syncingCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">同步失败</div>
          <div class="stat-value warn">{{ panel?.failedCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">今日已同步</div>
          <div class="stat-value">{{ panel?.todaySyncedCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">网络状态</div>
          <div class="stat-value">
            <el-tag :type="panel?.networkStatus === 'ONLINE' ? 'success' : 'danger'" size="large">
              {{ panel?.networkStatus === 'ONLINE' ? '在线' : '离线' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <p class="meta">最后同步：{{ panel?.lastSyncTime || '-' }}　探测时间：{{ panel?.lastCheckTime || '-' }}</p>
    <el-alert v-if="panel?.lastError" :title="panel.lastError" type="warning" :closable="false" />

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>失败记录</template>
      <el-table :data="failed" stripe>
        <el-table-column prop="reportNo" label="报工单号" width="150" />
        <el-table-column prop="moNo" label="工单" width="140" />
        <el-table-column prop="processName" label="工序" width="120" />
        <el-table-column prop="syncStatus" label="状态" width="120">
          <template #default="{ row }">
            <OrderStatusTag :status="row.syncStatus" failed-label="同步失败" />
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="warning" @click="handleRetry(row)">手动重试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 8px;
}
.stat-value.warn {
  color: var(--el-color-danger);
}
.meta {
  margin: 12px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
