<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getReceiveBatchDetail,
  listReceiveBatches,
  syncReceiveBatchToErp,
  type ReceiveSubmitBatch,
  type ReceiveSubmitBatchDetail,
} from '@/api/receiveBatch'

const loading = ref(false)
const tableData = ref<ReceiveSubmitBatch[]>([])
const total = ref(0)
const query = ref({ current: 1, size: 20, billNo: '', erpSyncStatus: '' })

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ReceiveSubmitBatchDetail | null>(null)
const syncingBatchNo = ref('')

const batchErrorTip = computed(() => {
  const batch = detail.value?.batch
  if (!batch) return ''
  if (batch.erpSyncStatus === 'SUCCESS') return ''
  if (batch.erpSyncMessage) return batch.erpSyncMessage
  const failedLog = (detail.value?.syncLogs || []).find((l) => l.status === 'FAILED' && l.errorMessage)
  return failedLog?.errorMessage || ''
})

async function load() {
  loading.value = true
  try {
    const res = await listReceiveBatches(query.value)
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function showDetail(row: ReceiveSubmitBatch) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getReceiveBatchDetail(row.batchNo)
  } finally {
    detailLoading.value = false
  }
}

async function handleSync(row: ReceiveSubmitBatch) {
  await ElMessageBox.confirm(`确定将批次 ${row.batchNo} 同步至金蝶？`, '同步金蝶')
  syncingBatchNo.value = row.batchNo
  try {
    const res = await syncReceiveBatchToErp(row.batchNo)
    ElMessage.success(String(res.message || res.erpSyncMessage || '同步完成'))
    await load()
    if (drawerVisible.value && detail.value?.batch?.batchNo === row.batchNo) {
      await showDetail(row)
    }
  } finally {
    syncingBatchNo.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <span>收料部分入库批次</span>
      </template>
      <el-form :inline="true" class="mb-12">
        <el-form-item label="收料单号">
          <el-input v-model="query.billNo" clearable placeholder="RN..." @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="金蝶状态">
          <el-select v-model="query.erpSyncStatus" clearable placeholder="全部" style="width: 140px">
            <el-option label="待同步" value="PENDING" />
            <el-option label="已同步" value="SUCCESS" />
            <el-option label="同步失败" value="FAILED" />
            <el-option label="部分成功" value="PARTIAL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" border>
        <el-table-column prop="batchNo" label="提交批次" min-width="160" />
        <el-table-column prop="billNo" label="收料通知单" min-width="140" />
        <el-table-column prop="lineCount" label="行数" width="70" />
        <el-table-column prop="totalQty" label="数量" width="90" />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="submitTime" label="提交时间" min-width="120">
          <template #default="{ row }">
            <WmsDateText :value="row.submitTime" />
          </template>
        </el-table-column>
        <el-table-column label="金蝶同步" width="110">
          <template #default="{ row }">
            <WmsStatusTag
              :status="row.erpSyncStatus"
              pending-label="待同步"
              failed-label="同步失败"
              partial-label="部分成功"
            />
          </template>
        </el-table-column>
        <el-table-column prop="erpBillNo" label="金蝶单号" min-width="120" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="row.erpSyncStatus !== 'SUCCESS'"
              link
              type="primary"
              :loading="syncingBatchNo === row.batchNo"
              @click="handleSync(row)"
            >
              同步金蝶
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-16"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </el-card>

    <el-drawer v-model="drawerVisible" title="收料入库批次详情" size="640px">
      <div v-loading="detailLoading">
        <template v-if="detail?.batch">
          <el-alert
            v-if="batchErrorTip"
            class="mb-12"
            type="error"
            :closable="false"
            show-icon
            title="同步报错"
            :description="batchErrorTip"
          />

          <el-descriptions :column="1" border>
            <el-descriptions-item label="提交批次">{{ detail.batch.batchNo }}</el-descriptions-item>
            <el-descriptions-item label="收料通知单">{{ detail.batch.billNo }}</el-descriptions-item>
            <el-descriptions-item label="行数 / 数量">
              {{ detail.batch.lineCount }} / {{ detail.batch.totalQty }}
            </el-descriptions-item>
            <el-descriptions-item label="操作人">{{ detail.batch.operatorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="设备号">{{ detail.batch.deviceNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">
              <WmsDateText :value="detail.batch.submitTime" />
            </el-descriptions-item>
            <el-descriptions-item label="金蝶同步">
              <WmsStatusTag
                :status="detail.batch.erpSyncStatus"
                pending-label="待同步"
                failed-label="同步失败"
                partial-label="部分成功"
              />
            </el-descriptions-item>
            <el-descriptions-item label="金蝶单号">{{ detail.batch.erpBillNo || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.batch.erpSyncMessage" label="同步消息">
              <span :class="{ 'err-text': detail.batch.erpSyncStatus !== 'SUCCESS' }">
                {{ detail.batch.erpSyncMessage }}
              </span>
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="detail.batch.erpSyncStatus !== 'SUCCESS'" class="sync-actions">
            <el-button
              type="primary"
              :loading="syncingBatchNo === detail.batch.batchNo"
              @click="handleSync(detail.batch)"
            >
              同步金蝶
            </el-button>
          </div>

          <div class="section-title">入库明细</div>
          <el-table :data="detail.records || []" border size="small">
            <el-table-column prop="recordNo" label="记录号" min-width="120" show-overflow-tooltip />
            <el-table-column prop="materialCode" label="物料" min-width="110" />
            <el-table-column prop="materialName" label="名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="batchNo" label="批次" min-width="90" />
            <el-table-column prop="warehouseCode" label="仓库" width="80" />
            <el-table-column prop="locationCode" label="库位" min-width="90" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="同步" width="90">
              <template #default="{ row }">
                <WmsStatusTag
                  :status="row.erpSyncStatus"
                  pending-label="待同步"
                  failed-label="失败"
                  partial-label="部分"
                />
              </template>
            </el-table-column>
            <el-table-column prop="erpSyncMessage" label="行报错" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span :class="{ 'err-text': row.erpSyncStatus === 'FAILED' }">
                  {{ row.erpSyncMessage || '-' }}
                </span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!(detail.records && detail.records.length)" description="暂无入库明细" :image-size="64" />

          <div class="section-title">金蝶同步日志</div>
          <el-table :data="detail.syncLogs || []" border size="small">
            <el-table-column prop="createTime" label="时间" width="150">
              <template #default="{ row }">
                <WmsDateText :value="row.createTime" />
              </template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <WmsResultTag :status="row.status" />
              </template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip />
            <el-table-column prop="retryCount" label="重试" width="70" />
          </el-table>
          <el-empty v-if="!(detail.syncLogs && detail.syncLogs.length)" description="暂无同步日志" :image-size="64" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.mb-12 { margin-bottom: 12px; }
.mt-16 { margin-top: 16px; justify-content: flex-end; }
.section-title {
  margin: 20px 0 12px;
  font-weight: 600;
  color: #0f172a;
}
.sync-actions {
  margin-top: 16px;
}
.err-text {
  color: #dc2626;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
