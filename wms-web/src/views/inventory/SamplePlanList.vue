<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createSamplePlan,
  getSampleAlerts,
  getSampleChecklist,
  getSamplePlans,
  submitSampleResult,
  type SamplePlan,
} from '@/api/inventoryExt'
import MaterialSelectInput from '@/components/MaterialSelectInput.vue'
import { applyMaterialBasic, type Material } from '@/api/material'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const loading = ref(false)
const tableData = ref<SamplePlan[]>([])
const total = ref(0)
const query = reactive({ planNo: '', status: '', current: 1, size: 20 })
const alerts = ref<Record<string, unknown>[]>([])
const checklist = ref<Record<string, unknown>[]>([])
const checklistVisible = ref(false)
const currentPlanNo = ref('')
const resultForm = reactive({ materialCode: '', materialName: '', batchNo: '', locationCode: '', qualityStatus: 'QUALIFIED', remark: '' })

function onMaterialSelect(material: Material) {
  applyMaterialBasic(resultForm as unknown as Record<string, unknown>, material)
}

async function loadData() {
  loading.value = true
  try {
    const res = await getSamplePlans(query)
    tableData.value = res.records
    total.value = res.total
    alerts.value = await getSampleAlerts()
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  await createSamplePlan('WH001')
  ElMessage.success('抽检计划已创建')
  loadData()
}

async function openChecklist(row: SamplePlan) {
  currentPlanNo.value = row.planNo
  checklist.value = await getSampleChecklist(row.planNo)
  checklistVisible.value = true
}

async function submitResult() {
  await submitSampleResult({ planNo: currentPlanNo.value, ...resultForm })
  ElMessage.success('抽检结果已记录')
  checklist.value = await getSampleChecklist(currentPlanNo.value)
}

onMounted(loadData)
</script>

<template>
  <el-row :gutter="16">
    <el-col :span="16">
      <el-card shadow="never">
        <el-form :inline="true">
          <el-form-item>
            <el-button type="primary" @click="loadData">刷新</el-button>
            <el-button @click="handleCreate">制定抽检计划</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="tableData" stripe>
          <el-table-column prop="planNo" label="计划号" width="160" />
          <el-table-column prop="warehouseCode" label="仓库" width="100" />
          <el-table-column prop="planDate" label="计划日期" width="120">
            <template #default="{ row }">
              <WmsDateText :value="row.planDate" />
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <OrderStatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="openChecklist(row)">抽检清单</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>
    <el-col :span="8">
      <el-card shadow="never" header="异常预警">
        <el-empty v-if="!alerts.length" description="暂无异常" />
        <el-alert
          v-for="(a, i) in alerts"
          :key="i"
          :title="`${a.materialCode} / ${a.qualityStatus}`"
          type="warning"
          show-icon
          class="mb-8"
        />
      </el-card>
    </el-col>
  </el-row>

  <el-dialog v-model="checklistVisible" title="抽检清单" width="640px">
    <el-table :data="checklist" size="small" stripe>
      <el-table-column prop="materialCode" label="物料" />
      <el-table-column prop="batchNo" label="批次" />
      <el-table-column prop="locationCode" label="库位" />
      <el-table-column prop="stockQty" label="库存" />
    </el-table>
    <el-divider>记录抽检结果</el-divider>
    <el-form :inline="true">
      <el-form-item label="物料">
        <MaterialSelectInput v-model="resultForm.materialCode" @select="onMaterialSelect" />
      </el-form-item>
      <el-form-item v-if="resultForm.materialName" label="名称">
        <span>{{ resultForm.materialName }}</span>
      </el-form-item>
      <el-form-item label="质量">
        <WmsSelect v-model="resultForm.qualityStatus">
          <el-option label="合格" value="QUALIFIED" />
          <el-option label="不合格" value="UNQUALIFIED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item><el-button type="primary" @click="submitResult">提交</el-button></el-form-item>
    </el-form>
  </el-dialog>
</template>

<style scoped>.mb-8 { margin-bottom: 8px; }</style>
