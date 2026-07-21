<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPdaInboundRecords,
  getPdaInboundRecord,
  auditPdaInboundRecord,
  reversePdaInboundRecord,
  resyncPdaInboundRecord,
  getPdaInboundSyncLogs,
  type PdaInboundRecord,
  type ErpSyncLog,
} from '@/api/pdaInboundRecord'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'
import PrintActions from '@/components/PrintActions.vue'

const tableData = ref<PdaInboundRecord[]>([])
const total = ref(0)
const query = reactive({
  recordNo: '',
  materialCode: '',
  warehouseCode: '',
  status: '',
  erpSyncStatus: '',
  current: 1,
  size: 20,
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const current = ref<PdaInboundRecord | null>(null)
const syncLogs = ref<ErpSyncLog[]>([])

const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await getPdaInboundRecords(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function showDetail(row: PdaInboundRecord) {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    current.value = await getPdaInboundRecord(row.recordNo!)
    syncLogs.value = await getPdaInboundSyncLogs(row.recordNo!)
  } finally {
    detailLoading.value = false
  }
}

async function handleAudit(recordNo: string) {
  await ElMessageBox.confirm(`确定审核入库记录 ${recordNo}？`, '审核确认')
  await auditPdaInboundRecord(recordNo)
  ElMessage.success('审核成功')
  if (current.value?.recordNo === recordNo) {
    current.value = await getPdaInboundRecord(recordNo)
  }
  loadData()
}

async function handleReverse(recordNo: string) {
  const { value } = await ElMessageBox.prompt('请输入冲销原因', '冲销确认', {
    confirmButtonText: '确认冲销',
    cancelButtonText: '取消',
    inputPlaceholder: '冲销原因',
  })
  await reversePdaInboundRecord(recordNo, value)
  ElMessage.success('冲销成功')
  if (current.value?.recordNo === recordNo) {
    current.value = await getPdaInboundRecord(recordNo)
  }
  loadData()
}

async function handleResync(recordNo: string) {
  await ElMessageBox.confirm(`确定重新同步金蝶？`, '重同步')
  const result = await resyncPdaInboundRecord(recordNo)
  ElMessage.success(result.erpSyncStatus === 'SUCCESS' ? '同步成功' : '同步失败，请查看日志')
  if (current.value?.recordNo === recordNo) {
    current.value = result
    syncLogs.value = await getPdaInboundSyncLogs(recordNo)
  }
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="记录号">
        <el-input v-model="query.recordNo" clearable placeholder="PIR..." />
      </el-form-item>
      <el-form-item label="物料">
        <el-input v-model="query.materialCode" clearable />
      </el-form-item>
      <el-form-item label="仓库">
        <el-input v-model="query.warehouseCode" clearable placeholder="WH01" />
      </el-form-item>
      <el-form-item label="状态">
        <WmsSelect v-model="query.status" clearable placeholder="全部">
          <el-option label="已提交" value="SUBMITTED" />
          <el-option label="已审核" value="AUDITED" />
          <el-option label="已冲销" value="REVERSED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="同步状态">
        <WmsSelect v-model="query.erpSyncStatus" clearable placeholder="全部">
          <el-option label="待同步" value="PENDING" />
          <el-option label="已同步" value="SUCCESS" />
          <el-option label="同步失败" value="FAILED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="recordNo" label="记录号" width="160" />
      <el-table-column prop="materialCode" label="物料编码" width="130" />
      <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="warehouseCode" label="仓库" width="80" />
      <el-table-column prop="locationCode" label="库位" width="130" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="批次" width="100" />
      <el-table-column prop="quantity" label="数量" width="80" />
      <el-table-column prop="operatorName" label="操作人" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <OrderStatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="金蝶同步" width="100">
        <template #default="{ row }">
          <OrderStatusTag
            :status="row.erpSyncStatus"
            pending-label="待同步"
            failed-label="同步失败"
            partial-label="部分成功"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="入库时间" width="120">
        <template #default="{ row }">
          <WmsDateText :value="row.createTime" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
          <PrintActions biz="pda_inbound" :doc-no="row.recordNo" />
          <el-button
            v-if="row.status === 'SUBMITTED'"
            link type="success"
            @click="handleAudit(row.recordNo!)"
          >审核</el-button>
          <el-button
            v-if="row.status !== 'REVERSED'"
            link type="warning"
            @click="handleReverse(row.recordNo!)"
          >冲销</el-button>
          <el-button
            v-if="row.erpSyncStatus === 'FAILED' || row.erpSyncStatus === 'PENDING'"
            link type="danger"
            @click="handleResync(row.recordNo!)"
          >重同步</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      class="pagination"
      @current-change="loadData"
    />
  </el-card>

  <el-drawer v-model="drawerVisible" title="PDA入库记录详情" size="520px">
    <div v-loading="detailLoading">
      <template v-if="current">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="记录号">{{ current.recordNo }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ current.materialCode }} / {{ current.materialName }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ current.specification || '-' }}</el-descriptions-item>
          <el-descriptions-item label="仓库/库位">{{ current.warehouseCode }} / {{ current.locationCode }}</el-descriptions-item>
          <el-descriptions-item label="批次">{{ current.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ current.quantity }} {{ current.unitCode }}</el-descriptions-item>
          <el-descriptions-item label="条码">{{ current.barcodeContent }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ current.operatorName }} ({{ current.deviceNo || '-' }})</el-descriptions-item>
          <el-descriptions-item label="状态">
            <OrderStatusTag :status="current.status" />
          </el-descriptions-item>
          <el-descriptions-item label="金蝶单号">{{ current.erpBillNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="同步状态">
            <WmsStatusTag
              :status="current.erpSyncStatus"
              pending-label="待同步"
              failed-label="同步失败"
              partial-label="部分成功"
            />
          </el-descriptions-item>
          <el-descriptions-item label="同步消息">{{ current.erpSyncMessage || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核">
            {{ current.auditorName || '-' }}
            <WmsDateText v-if="current.auditTime" :value="current.auditTime" />
          </el-descriptions-item>
          <el-descriptions-item label="冲销">{{ current.reverseBy || '-' }} {{ current.reverseReason || '' }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin: 20px 0 12px">金蝶同步日志</h4>
        <el-table :data="syncLogs" size="small" stripe>
          <el-table-column prop="createTime" label="时间" width="120">
            <template #default="{ row }">
              <WmsDateText :value="row.createTime" />
            </template>
          </el-table-column>
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <WmsResultTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
          <el-table-column prop="retryCount" label="重试次数" width="80" />
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
