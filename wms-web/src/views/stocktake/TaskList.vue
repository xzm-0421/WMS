<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getStockcheckTasks, type StockcheckTask } from '@/api/stockcheck'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const loading = ref(false)
const tableData = ref<StockcheckTask[]>([])
const total = ref(0)
const query = reactive({ planNo: '', warehouseCode: '', status: '', current: 1, size: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await getStockcheckTasks(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="计划编号">
        <el-input v-model="query.planNo" clearable />
      </el-form-item>
      <el-form-item label="仓库">
        <el-input v-model="query.warehouseCode" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <WmsSelect v-model="query.status" clearable placeholder="全部">
          <el-option label="待盘点" value="PENDING" />
          <el-option label="盘点中" value="COUNTING" />
          <el-option label="已完成" value="COMPLETED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="taskNo" label="任务编号" width="160" />
      <el-table-column prop="planNo" label="计划编号" width="160" />
      <el-table-column prop="warehouseCode" label="仓库" width="100" />
      <el-table-column prop="locationCode" label="库位" width="120" />
      <el-table-column prop="assigneeId" label="盘点人" width="100" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.status" pending-label="待盘点" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170">
        <template #default="{ row }">
          <WmsDateText :value="row.createTime" />
        </template>
      </el-table-column>
      <el-table-column prop="completeTime" label="完成时间" width="170">
        <template #default="{ row }">
          <WmsDateText :value="row.completeTime" />
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
