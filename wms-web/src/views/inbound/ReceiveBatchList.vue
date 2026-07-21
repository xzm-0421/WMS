<script setup lang="ts">

import { onMounted, ref } from 'vue'

import { ElMessage, ElMessageBox } from 'element-plus'

import { listReceiveBatches, syncReceiveBatchToErp, type ReceiveSubmitBatch } from '@/api/receiveBatch'



const loading = ref(false)

const tableData = ref<ReceiveSubmitBatch[]>([])

const total = ref(0)

const query = ref({ current: 1, size: 20, billNo: '', erpSyncStatus: '' })



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



async function handleSync(row: ReceiveSubmitBatch) {

  await ElMessageBox.confirm(`确定将批次 ${row.batchNo} 同步至金蝶？`, '同步金蝶')

  const res = await syncReceiveBatchToErp(row.batchNo)

  ElMessage.success(res.message || '同步完成')

  load()

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

        <el-table-column label="操作" width="120" fixed="right">

          <template #default="{ row }">

            <el-button

              v-if="row.erpSyncStatus !== 'SUCCESS'"

              link

              type="primary"

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

  </div>

</template>



<style scoped>

.page { padding: 16px; }

.mb-12 { margin-bottom: 12px; }

.mt-16 { margin-top: 16px; justify-content: flex-end; }

</style>


