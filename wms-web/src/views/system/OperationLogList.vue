<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getOperationLogs, type OperationLog } from '@/api/system'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const loading = ref(false)
const tableData = ref<OperationLog[]>([])
const total = ref(0)
const timeRange = ref<[string, string] | null>(null)
const query = reactive({
  operatorName: '',
  module: '',
  operationType: '',
  responseResult: '',
  current: 1,
  size: 20,
})

const MODULES = ['认证', '轻MES', '系统管理']
const TYPES = ['CREATE', 'UPDATE', 'DELETE', 'SUBMIT', 'RETRY', 'CANCEL', 'RESET_PASSWORD', 'LOGIN', 'COMPLETE', 'SECONDARY', 'CLOSE']

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { ...query }
    if (timeRange.value && timeRange.value.length === 2) {
      params.start = timeRange.value[0]
      params.end = timeRange.value[1]
    }
    const res = await getOperationLogs(params)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function reset() {
  query.operatorName = ''
  query.module = ''
  query.operationType = ''
  query.responseResult = ''
  timeRange.value = null
  query.current = 1
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="操作人">
        <el-input v-model="query.operatorName" clearable />
      </el-form-item>
      <el-form-item label="模块">
        <WmsSelect v-model="query.module" clearable placeholder="全部">
          <el-option v-for="m in MODULES" :key="m" :label="m" :value="m" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="操作类型">
        <WmsSelect v-model="query.operationType" clearable placeholder="全部">
          <el-option v-for="t in TYPES" :key="t" :label="t" :value="t" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="结果">
        <WmsSelect v-model="query.responseResult" clearable placeholder="全部">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAIL" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          range-separator="至"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column label="操作时间" width="170">
        <template #default="{ row }"><WmsDateText :value="row.operationTime" /></template>
      </el-table-column>
      <el-table-column prop="operatorName" label="操作人" width="110" />
      <el-table-column prop="module" label="模块" width="100" />
      <el-table-column prop="operationType" label="类型" width="130" />
      <el-table-column prop="operationContent" label="内容" min-width="180" show-overflow-tooltip />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <OrderStatusTag :status="row.responseResult === 'SUCCESS' ? 'SUCCESS' : 'FAILED'" />
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP" width="130" />
      <el-table-column prop="requestParams" label="参数" min-width="220" show-overflow-tooltip />
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
