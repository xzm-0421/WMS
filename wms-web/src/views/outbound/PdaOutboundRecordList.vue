<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPdaOutboundBatchDetail,
  listPdaOutboundBatches,
  PDA_OUTBOUND_BILL_TYPE_LABELS,
  syncPdaOutboundBatchToErp,
  type PdaOutboundBatch,
  type PdaOutboundBatchDetail,
} from '@/api/pdaOutboundBatch'

const loading = ref(false)
const tableData = ref<PdaOutboundBatch[]>([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  billNo: '',
  batchNo: '',
  billType: '',
  erpSyncStatus: '',
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PdaOutboundBatchDetail | null>(null)
const syncingBatchNo = ref('')

function billTypeLabel(code?: string) {
  if (!code) return '-'
  return PDA_OUTBOUND_BILL_TYPE_LABELS[code] || code
}

async function load() {
  loading.value = true
  try {
    const res = await listPdaOutboundBatches(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function showDetail(row: PdaOutboundBatch) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getPdaOutboundBatchDetail(row.batchNo)
  } finally {
    detailLoading.value = false
  }
}

async function handleSync(row: PdaOutboundBatch) {
  const billLabel = billTypeLabel(row.billType) || '金蝶单据'
  await ElMessageBox.confirm(`确定将批次 ${row.batchNo} 同步至${billLabel}？`, '同步金蝶')
  syncingBatchNo.value = row.batchNo
  try {
    const res = await syncPdaOutboundBatchToErp(row.batchNo)
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
        <span>PDA出库记录</span>
      </template>

      <el-form :inline="true" class="mb-12">
        <el-form-item label="批次号">
          <el-input v-model="query.batchNo" clearable placeholder="RSB..." @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="单据号">
          <el-input v-model="query.billNo" clearable placeholder="通知单号" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="单据类型">
          <el-select v-model="query.billType" clearable placeholder="全部" style="width: 160px">
            <el-option
              v-for="(label, code) in PDA_OUTBOUND_BILL_TYPE_LABELS"
              :key="code"
              :label="label"
              :value="code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="同步状态">
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
        <el-table-column prop="batchNo" label="出库批次" min-width="170" />
        <el-table-column label="单据类型" min-width="130">
          <template #default="{ row }">
            {{ billTypeLabel(row.billType) }}
          </template>
        </el-table-column>
        <el-table-column prop="billNo" label="来源单号" min-width="140" />
        <el-table-column prop="supplierName" label="车间/对象" min-width="120" show-overflow-tooltip />
        <el-table-column prop="lineCount" label="行数" width="70" />
        <el-table-column prop="totalQty" label="数量" width="90" />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="deviceNo" label="设备" min-width="120" show-overflow-tooltip />
        <el-table-column prop="submitTime" label="出库时间" min-width="160">
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

    <el-drawer v-model="drawerVisible" title="出库批次详情" size="560px">
      <div v-loading="detailLoading">
        <template v-if="detail?.batch">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="出库批次">{{ detail.batch.batchNo }}</el-descriptions-item>
            <el-descriptions-item label="单据类型">{{ billTypeLabel(detail.batch.billType) }}</el-descriptions-item>
            <el-descriptions-item label="来源单号">{{ detail.batch.billNo }}</el-descriptions-item>
            <el-descriptions-item label="车间/对象">
              {{ detail.batch.supplierName || detail.batch.supplierCode || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="行数 / 数量">
              {{ detail.batch.lineCount }} / {{ detail.batch.totalQty }}
            </el-descriptions-item>
            <el-descriptions-item label="操作人">{{ detail.batch.operatorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="设备号">{{ detail.batch.deviceNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="出库时间">
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
              {{ detail.batch.erpSyncMessage }}
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

          <div class="section-title">库存扣减明细</div>
          <el-table :data="detail.lines || []" border size="small">
            <el-table-column prop="materialCode" label="物料" min-width="110" />
            <el-table-column prop="batchNo" label="批次" min-width="100" />
            <el-table-column prop="warehouseCode" label="仓库" width="90" />
            <el-table-column prop="locationCode" label="库位" min-width="100" />
            <el-table-column prop="transactionQty" label="数量" width="80" />
            <el-table-column prop="remark" label="条码/备注" min-width="120" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!(detail.lines && detail.lines.length)" description="暂无库存流水明细" :image-size="64" />
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
</style>
