<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approveTransfer,
  cancelTransfer,
  createTransfer,
  executeTransfer,
  getTransfers,
  type TransferOrder,
} from '@/api/inventoryExt'
import MaterialSelectInput from '@/components/MaterialSelectInput.vue'
import { applyMaterialBasic, type Material } from '@/api/material'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'
import PrintActions from '@/components/PrintActions.vue'

const loading = ref(false)
const tableData = ref<TransferOrder[]>([])
const total = ref(0)
const query = reactive({ transferNo: '', status: '', current: 1, size: 20 })
const dialogVisible = ref(false)
const form = reactive({
  sourceWarehouse: 'WH001',
  sourceLocation: 'LOC001',
  targetWarehouse: 'WH001',
  targetLocation: 'LOC002',
  materialCode: '',
  materialName: '',
  batchNo: '',
  transferQty: 1,
})

function onMaterialSelect(material: Material) {
  applyMaterialBasic(form as unknown as Record<string, unknown>, material)
}

async function loadData() {
  loading.value = true
  try {
    const res = await getTransfers(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  await createTransfer(form)
  ElMessage.success('调拨申请已提交')
  dialogVisible.value = false
  loadData()
}

async function handleApprove(row: TransferOrder) {
  await approveTransfer(row.transferNo)
  ElMessage.success('已审批')
  loadData()
}

async function handleExecute(row: TransferOrder) {
  await executeTransfer(row.transferNo)
  ElMessage.success('调拨已完成')
  loadData()
}

async function handleCancel(row: TransferOrder) {
  await cancelTransfer(row.transferNo)
  ElMessage.success('已取消')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true">
      <el-form-item label="调拨单号"><el-input v-model="query.transferNo" clearable /></el-form-item>
      <el-form-item label="状态">
        <WmsSelect v-model="query.status" clearable placeholder="全部" @change="loadData">
          <el-option label="待审批" value="PENDING" />
          <el-option label="执行中" value="EXECUTING" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="dialogVisible = true">新建调拨</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="transferNo" label="调拨单号" width="160" />
      <el-table-column prop="materialCode" label="物料" width="120" />
      <el-table-column prop="sourceLocation" label="源库位" width="100" />
      <el-table-column prop="targetLocation" label="目标库位" width="100" />
      <el-table-column prop="transferQty" label="数量" width="80" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.status" pending-label="待审批" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="primary" @click="handleApprove(row)">审批</el-button>
          <el-button v-if="row.status === 'PENDING' || row.status === 'EXECUTING'" link type="success" @click="handleExecute(row)">执行</el-button>
          <el-button v-if="row.status !== 'COMPLETED'" link type="danger" @click="handleCancel(row)">取消</el-button>
          <PrintActions biz="transfer_order" :doc-no="row.transferNo" />
        </template>
      </el-table-column>
    </el-table>
  </el-card>
  <el-dialog v-model="dialogVisible" title="库存调拨申请" width="520px">
    <el-form label-width="100px">
      <el-form-item label="源库位"><el-input v-model="form.sourceLocation" /></el-form-item>
      <el-form-item label="目标库位"><el-input v-model="form.targetLocation" /></el-form-item>
      <el-form-item label="物料">
        <MaterialSelectInput v-model="form.materialCode" @select="onMaterialSelect" />
      </el-form-item>
      <el-form-item v-if="form.materialName" label="物料名称">
        <span>{{ form.materialName }}</span>
      </el-form-item>
      <el-form-item label="数量"><el-input-number v-model="form.transferQty" :min="0.001" :precision="4" :step="0.001" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleCreate">提交</el-button>
    </template>
  </el-dialog>
</template>
