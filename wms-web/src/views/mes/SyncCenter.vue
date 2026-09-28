<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getMesOutbox,
  getMesReports,
  getMesSyncPanel,
  getMesTransfers,
  retryMesOutbox,
  retryMesReport,
  retryMesTransfer,
  type MesErpOutbox,
  type MesSyncPanel,
} from '@/api/mes'
import { useUserStore } from '@/stores/user'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

interface UnifiedRow {
  kind: 'REPORT' | 'TRANSFER'
  no: string
  moNo?: string
  process: string
  syncStatus?: string
  failReason?: string
}

const userStore = useUserStore()
const panel = ref<MesSyncPanel | null>(null)
const failed = ref<UnifiedRow[]>([])
const outboxFailed = ref<MesErpOutbox[]>([])
const typeFilter = ref<'' | 'REPORT' | 'TRANSFER'>('')
let timer: number | undefined

const filtered = computed(() =>
  typeFilter.value ? failed.value.filter((r) => r.kind === typeFilter.value) : failed.value,
)

async function load() {
  panel.value = await getMesSyncPanel()
  const [rFailed, rManual, tFailed, tManual, obFailed, obManual] = await Promise.all([
    getMesReports({ syncStatus: 'FAILED', current: 1, size: 50 }),
    getMesReports({ syncStatus: 'MANUAL_REQUIRED', current: 1, size: 50 }),
    getMesTransfers({ syncStatus: 'FAILED', current: 1, size: 50 }),
    getMesTransfers({ syncStatus: 'MANUAL_REQUIRED', current: 1, size: 50 }),
    getMesOutbox({ syncStatus: 'FAILED', current: 1, size: 50 }),
    getMesOutbox({ syncStatus: 'MANUAL_REQUIRED', current: 1, size: 50 }),
  ])
  const reports: UnifiedRow[] = [...rFailed.records, ...rManual.records].map((r) => ({
    kind: 'REPORT',
    no: r.reportNo || '',
    moNo: r.moNo,
    process: r.processName || r.processCode || '',
    syncStatus: r.syncStatus,
    failReason: r.failReason,
  }))
  const transfers: UnifiedRow[] = [...tFailed.records, ...tManual.records].map((t) => ({
    kind: 'TRANSFER',
    no: t.transferNo || '',
    moNo: t.moNo,
    process: `${t.fromProcessName || t.fromProcessCode || ''}→${t.toProcessName || t.toProcessCode || ''}`,
    syncStatus: t.syncStatus,
    failReason: t.failReason,
  }))
  failed.value = [...reports, ...transfers]
  outboxFailed.value = [...obFailed.records, ...obManual.records]
}

async function handleRetry(row: UnifiedRow) {
  if (row.kind === 'REPORT') {
    await retryMesReport(row.no)
  } else {
    await retryMesTransfer(row.no)
  }
  ElMessage.success('已加入重试队列')
  load()
}

async function handleOutboxRetry(row: MesErpOutbox) {
  await retryMesOutbox(row.id!)
  ElMessage.success('已加入重试队列')
  load()
}

function canRetry(row: UnifiedRow) {
  return row.kind === 'REPORT'
    ? userStore.hasPermission('mes:report:retry')
    : userStore.hasPermission('mes:transfer:retry')
}

function outboxTypeLabel(type?: string) {
  return type === 'REWORK' ? '返工工单' : '不良单'
}

function outboxActionLabel(action?: string) {
  return action === 'SUBMIT' ? '提交' : '新增'
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
          <div class="stat-sub">
            报工 {{ panel?.reportPendingCount ?? 0 }} · 转移 {{ panel?.transferPendingCount ?? 0 }}
          </div>
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
          <div class="stat-sub">
            报工 {{ panel?.reportFailedCount ?? 0 }} · 转移 {{ panel?.transferFailedCount ?? 0 }}
          </div>
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

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">不良单同步</div>
          <div class="stat-value">
            待 {{ panel?.defectPendingCount ?? 0 }} · 失败
            <span class="warn">{{ panel?.defectFailedCount ?? 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">返工工单同步</div>
          <div class="stat-value">
            待 {{ panel?.reworkPendingCount ?? 0 }} · 失败
            <span class="warn">{{ panel?.reworkFailedCount ?? 0 }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <p class="meta">最后同步：{{ panel?.lastSyncTime || '-' }}　探测时间：{{ panel?.lastCheckTime || '-' }}</p>
    <el-alert v-if="panel?.lastError" :title="panel.lastError" type="warning" :closable="false" />

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-head">
          <span>报工 / 转移失败记录</span>
          <el-radio-group v-model="typeFilter" size="small">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="REPORT">报工</el-radio-button>
            <el-radio-button label="TRANSFER">转移</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table :data="filtered" stripe>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.kind === 'REPORT' ? 'primary' : 'warning'" size="small">
              {{ row.kind === 'REPORT' ? '报工' : '转移' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="no" label="单据号" width="160" />
        <el-table-column prop="moNo" label="工单" width="140" />
        <el-table-column prop="process" label="工序" width="150" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <OrderStatusTag :status="row.syncStatus" failed-label="同步失败" />
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="canRetry(row)" link type="warning" @click="handleRetry(row)">手动重试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>不良 / 返工回传失败记录</template>
      <el-table :data="outboxFailed" stripe>
        <el-table-column label="单据" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ outboxTypeLabel(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizNo" label="关联单号" width="160" />
        <el-table-column label="动作" width="90">
          <template #default="{ row }">{{ outboxActionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <OrderStatusTag :status="row.syncStatus" failed-label="同步失败" />
          </template>
        </el-table-column>
        <el-table-column prop="erpBillNo" label="ERP单号" width="150" />
        <el-table-column prop="failReason" label="失败原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPermission('mes:sync:panel')"
              link
              type="warning"
              @click="handleOutboxRetry(row)"
            >
              手动重试
            </el-button>
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
.stat-value.warn,
.warn {
  color: var(--el-color-danger);
}
.stat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.meta {
  margin: 12px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
