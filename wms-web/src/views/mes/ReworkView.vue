<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  createMesDefect,
  getMesDefects,
  getMesReportContext,
  getMesReworkSequence,
  type MesDefect,
  type MesOpPlan,
  type MesReworkOp,
} from '@/api/mes'

const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('mes:rework:create'))

const loading = ref(false)
const tableData = ref<MesDefect[]>([])
const total = ref(0)
const query = reactive({ moNo: '', reworkStatus: '', current: 1, size: 20 })

const form = reactive({
  moNo: '',
  sourceProcessCode: '',
  defectQty: 1,
  defectType: 'APPEARANCE',
  defectDesc: '',
  ownerName: '',
  reworkProcessCodes: [] as string[],
})
const plans = ref<MesOpPlan[]>([])
const seqVisible = ref(false)
const seqOps = ref<MesReworkOp[]>([])
const seqTitle = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await getMesDefects(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadPlans() {
  if (!form.moNo.trim()) return
  const ctx = await getMesReportContext(form.moNo.trim())
  plans.value = ctx.plans || []
  if (plans.value.length && !form.sourceProcessCode) {
    form.sourceProcessCode = plans.value[0].processCode || ''
  }
}

async function handleCreate() {
  const vo = await createMesDefect({ ...form, moNo: form.moNo.trim() })
  ElMessage.success(`已创建返工 ${vo.defect?.defectNo}`)
  loadData()
}

async function showSeq(row: MesDefect) {
  const vo = await getMesReworkSequence(row.defectNo!)
  seqTitle.value = `返工序列 ${row.defectNo}`
  seqOps.value = vo.operations || []
  seqVisible.value = true
}

onMounted(loadData)
</script>

<template>
  <el-row :gutter="16">
    <el-col v-if="canCreate" :span="10">
      <el-card shadow="never">
        <template #header>发起返工</template>
        <el-form :model="form" label-width="100px">
          <el-form-item label="源工单号" required>
            <el-input v-model="form.moNo" @blur="loadPlans" />
          </el-form-item>
          <el-form-item label="源工序" required>
            <WmsSelect v-model="form.sourceProcessCode" filterable>
              <el-option
                v-for="p in plans"
                :key="p.processCode"
                :label="`${p.processName} 已报${p.reportedQty}`"
                :value="p.processCode"
              />
            </WmsSelect>
          </el-form-item>
          <el-form-item label="不良数量" required>
            <el-input-number v-model="form.defectQty" :min="0.0001" :precision="4" />
          </el-form-item>
          <el-form-item label="不良类型" required>
            <WmsSelect v-model="form.defectType">
              <el-option label="外观" value="APPEARANCE" />
              <el-option label="尺寸" value="SIZE" />
              <el-option label="功能" value="FUNCTION" />
              <el-option label="其他" value="OTHER" />
            </WmsSelect>
          </el-form-item>
          <el-form-item label="返工工序">
            <WmsSelect v-model="form.reworkProcessCodes" multiple filterable placeholder="空则按工艺路线自动生成">
              <el-option
                v-for="p in plans"
                :key="'rw-' + p.processCode"
                :label="`${p.processName} (${p.processCode})`"
                :value="p.processCode"
              />
            </WmsSelect>
          </el-form-item>
          <el-form-item label="不良描述">
            <el-input v-model="form.defectDesc" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="责任人">
            <el-input v-model="form.ownerName" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleCreate">发起返工</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>
    <el-col :span="canCreate ? 14 : 24">
      <el-card shadow="never">
        <template #header>不良 / 返工单</template>
        <el-form :inline="true">
          <el-form-item label="工单号">
            <el-input v-model="query.moNo" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadData">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="tableData" stripe>
          <el-table-column prop="defectNo" label="不良单号" width="150" />
          <el-table-column prop="moNo" label="工单" width="130" />
          <el-table-column prop="sourceProcessName" label="源工序" width="110" />
          <el-table-column prop="defectQty" label="不良数" width="80" />
          <el-table-column prop="defectType" label="类型" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><OrderStatusTag :status="row.reworkStatus" /></template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="primary" @click="showSeq(row)">查看返工序列</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="query.current"
          :total="total"
          layout="total, prev, pager, next"
          style="margin-top: 16px"
          @current-change="loadData"
        />
      </el-card>
    </el-col>
  </el-row>

  <el-dialog v-model="seqVisible" :title="seqTitle" width="640px">
    <el-table :data="seqOps" stripe>
      <el-table-column prop="seqNo" label="#" width="50" />
      <el-table-column prop="processCode" label="工序编码" width="120" />
      <el-table-column prop="processName" label="工序名称" />
      <el-table-column prop="planQty" label="计划" width="80" />
      <el-table-column prop="reportedQty" label="已报" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><OrderStatusTag :status="row.opStatus" /></template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>
