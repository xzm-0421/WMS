<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getQcOrders, submitQcResult, type QcOrder } from '@/api/quality'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'
import PrintActions from '@/components/PrintActions.vue'

const loading = ref(false)
const tableData = ref<QcOrder[]>([])
const total = ref(0)
const query = reactive({ qcNo: '', materialCode: '', status: '', current: 1, size: 20 })

const completeVisible = ref(false)
const currentOrder = ref<QcOrder | null>(null)
const resultForm = reactive({ result: 'PASS', remark: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await getQcOrders(query)
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openComplete(row: QcOrder) {
  currentOrder.value = row
  resultForm.result = 'PASS'
  resultForm.remark = ''
  completeVisible.value = true
}

async function handleComplete() {
  if (!currentOrder.value?.qcNo) return
  await submitQcResult(currentOrder.value.qcNo, resultForm)
  ElMessage.success('质检完成')
  completeVisible.value = false
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="质检单号">
        <el-input v-model="query.qcNo" clearable />
      </el-form-item>
      <el-form-item label="物料编码">
        <el-input v-model="query.materialCode" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <WmsSelect v-model="query.status" clearable placeholder="全部">
          <el-option label="待检" value="PENDING" />
          <el-option label="已完成" value="COMPLETED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="qcNo" label="质检单号" width="160" />
      <el-table-column prop="sourceType" label="来源类型" width="100" />
      <el-table-column prop="sourceNo" label="来源单号" width="150" />
      <el-table-column prop="materialCode" label="物料编码" width="130" />
      <el-table-column prop="batchNo" label="批次" width="120" />
      <el-table-column prop="sampleQty" label="抽样数量" width="100" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <OrderStatusTag :status="row.status || 'PENDING'" pending-label="待检" />
        </template>
      </el-table-column>
      <el-table-column prop="result" label="结果" width="90">
        <template #default="{ row }">
          <OrderStatusTag v-if="row.result" :status="row.result" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="inspectorName" label="检验员" width="100" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING'"
            link
            type="primary"
            @click="openComplete(row)"
          >
            完成质检
          </el-button>
          <PrintActions biz="qc_order" :doc-no="row.qcNo" />
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

  <el-dialog v-model="completeVisible" title="完成质检" width="440px">
    <el-form :model="resultForm" label-width="90px">
      <el-form-item label="质检单号">
        <span>{{ currentOrder?.qcNo }}</span>
      </el-form-item>
      <el-form-item label="物料">
        <span>{{ currentOrder?.materialCode }}</span>
      </el-form-item>
      <el-form-item label="检验结果" required>
        <el-radio-group v-model="resultForm.result">
          <el-radio value="PASS">合格</el-radio>
          <el-radio value="FAIL">不合格</el-radio>
          <el-radio value="CONCESSION">让步接收</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="resultForm.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="completeVisible = false">取消</el-button>
      <el-button type="primary" @click="handleComplete">确定</el-button>
    </template>
  </el-dialog>
</template>
