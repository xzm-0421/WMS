<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getStockcheckDiffs,
  approveStockcheckDiff,
  rejectStockcheckDiff,
  type StockcheckDiff,
} from '@/api/stockcheck'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const loading = ref(false)
const tableData = ref<StockcheckDiff[]>([])
const total = ref(0)
const query = reactive({ taskNo: '', status: '', current: 1, size: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await getStockcheckDiffs(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleApprove(row: StockcheckDiff) {
  await ElMessageBox.confirm(`确定审批差异记录（物料 ${row.materialCode}）？将通过并调整库存。`, '提示')
  await approveStockcheckDiff(row.id!)
  ElMessage.success('审批成功')
  loadData()
}

async function handleReject(row: StockcheckDiff) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回盘点差异', {
    inputPlaceholder: '驳回原因',
    inputValidator: (v: string) => (v && v.trim() ? true : '请填写驳回原因'),
  })
  await rejectStockcheckDiff(row.id!, value)
  ElMessage.success('已驳回')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="任务编号">
        <el-input v-model="query.taskNo" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <WmsSelect v-model="query.status" clearable placeholder="全部">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="taskNo" label="任务编号" width="160" />
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="locationCode" label="库位" width="100" />
      <el-table-column prop="batchNo" label="批次" width="120" />
      <el-table-column prop="diffQty" label="差异数量" width="100" />
      <el-table-column prop="diffReason" label="差异原因" min-width="160" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.status" pending-label="待处理" />
        </template>
      </el-table-column>
      <el-table-column prop="approverId" label="处理人" width="100" />
      <el-table-column prop="approveTime" label="处理时间" width="170">
        <template #default="{ row }">
          <WmsDateText :value="row.approveTime" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="primary" @click="handleApprove(row)">审批</el-button>
            <el-button link type="danger" @click="handleReject(row)">驳回</el-button>
          </template>
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
