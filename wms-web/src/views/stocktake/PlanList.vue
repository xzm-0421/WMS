<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getStockcheckPlans,
  createStockcheckPlan,
  publishStockcheckPlan,
  deleteStockcheckPlan,
  type StockcheckPlan,
} from '@/api/stockcheck'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const loading = ref(false)
const tableData = ref<StockcheckPlan[]>([])
const total = ref(0)
const query = reactive({ planNo: '', warehouseCode: '', status: '', current: 1, size: 20 })

const dialogVisible = ref(false)
const form = reactive<StockcheckPlan>({
  planName: '',
  warehouseCode: 'WH01',
  planType: 'FULL',
  planDate: new Date().toISOString().slice(0, 10),
  remark: '',
})

async function loadData() {
  loading.value = true
  try {
    const res = await getStockcheckPlans(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  Object.assign(form, {
    planName: '',
    warehouseCode: 'WH01',
    planType: 'FULL',
    planDate: new Date().toISOString().slice(0, 10),
    remark: '',
  })
  dialogVisible.value = true
}

async function handleCreate() {
  await createStockcheckPlan(form)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadData()
}

async function handlePublish(row: StockcheckPlan) {
  await ElMessageBox.confirm(`确定发布盘点计划 ${row.planName}？发布后将生成盘点任务。`, '提示')
  await publishStockcheckPlan(row.planNo!)
  ElMessage.success('发布成功')
  loadData()
}

async function handleDelete(row: StockcheckPlan) {
  await ElMessageBox.confirm(`确定删除盘点计划 ${row.planName}？`, '提示')
  await deleteStockcheckPlan(row.planNo!)
  ElMessage.success('删除成功')
  loadData()
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
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="盘点中" value="COUNTING" />
          <el-option label="已完成" value="COMPLETED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="handleAdd">新建计划</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="planNo" label="计划编号" width="160" />
      <el-table-column prop="planName" label="计划名称" min-width="160" />
      <el-table-column prop="warehouseCode" label="仓库" width="100" />
      <el-table-column prop="planType" label="类型" width="100">
        <template #default="{ row }">
          {{ row.planType === 'FULL' ? '全盘' : row.planType === 'PARTIAL' ? '抽盘' : row.planType }}
        </template>
      </el-table-column>
      <el-table-column prop="planDate" label="计划日期" width="120">
        <template #default="{ row }">
          <WmsDateText :value="row.planDate" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <OrderStatusTag :status="row.status || 'DRAFT'" />
        </template>
      </el-table-column>
      <el-table-column prop="creatorName" label="创建人" width="100" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="primary"
            @click="handlePublish(row)"
          >
            发布
          </el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
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

  <el-dialog v-model="dialogVisible" title="新建盘点计划" width="520px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="计划名称" required>
        <el-input v-model="form.planName" />
      </el-form-item>
      <el-form-item label="仓库" required>
        <el-input v-model="form.warehouseCode" />
      </el-form-item>
      <el-form-item label="盘点类型">
        <WmsSelect v-model="form.planType">
          <el-option label="全盘" value="FULL" />
          <el-option label="抽盘" value="PARTIAL" />
          <el-option label="循环盘点" value="CYCLE" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="计划日期" required>
        <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleCreate">确定</el-button>
    </template>
  </el-dialog>
</template>
