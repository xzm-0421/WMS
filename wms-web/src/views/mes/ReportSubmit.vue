<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getMesReportContext,
  submitMesReport,
  type MesEquipment,
  type MesOpPlan,
  type MesReportContext,
} from '@/api/mes'

const loading = ref(false)
const submitting = ref(false)
const context = ref<MesReportContext | null>(null)
const form = reactive({
  moNo: '',
  reportType: 'NORMAL',
  processCode: '',
  qty: 1,
  weightKg: undefined as number | undefined,
  equipmentCode: '',
  defectNo: '',
  remark: '',
})

const plans = ref<MesOpPlan[]>([])
const equipment = ref<MesEquipment[]>([])

async function loadContext() {
  if (!form.moNo.trim()) {
    ElMessage.warning('请输入工单号')
    return
  }
  loading.value = true
  try {
    context.value = await getMesReportContext(form.moNo.trim())
    plans.value = context.value.plans || []
    equipment.value = context.value.equipment || []
    const types = context.value.allowedReportTypes || ['NORMAL']
    if (!types.includes(form.reportType)) {
      form.reportType = types[0]
    }
    if (plans.value.length && !form.processCode) {
      form.processCode = plans.value[0].processCode || ''
    }
    ElMessage.success('已加载工序计划')
  } finally {
    loading.value = false
  }
}

function currentPlan() {
  return plans.value.find((p) => p.processCode === form.processCode)
}

async function handleSubmit() {
  if (!form.moNo.trim() || !form.processCode || !form.equipmentCode) {
    ElMessage.warning('请填写工单、工序和设备')
    return
  }
  submitting.value = true
  try {
    const res = await submitMesReport({ ...form, moNo: form.moNo.trim() })
    ElMessage.success(`报工成功 ${res.reportNo}`)
    form.qty = 1
    form.remark = ''
    await loadContext()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="本期为 Web 报工。PDA / 工业平板扫码与电子秤对接后续再做。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :model="form" label-width="110px" style="max-width: 640px">
      <el-form-item label="工单号" required>
        <el-input v-model="form.moNo" placeholder="扫码或手工录入工单号" @keyup.enter="loadContext">
          <template #append>
            <el-button :loading="loading" @click="loadContext">查询</el-button>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item v-if="context?.typeHint">
        <el-alert :title="context.typeHint" type="warning" :closable="false" />
      </el-form-item>
      <el-form-item label="工序类型" required>
        <el-radio-group v-model="form.reportType">
          <el-radio
            :value="'NORMAL'"
            :disabled="!!context && !(context.allowedReportTypes || []).includes('NORMAL')"
          >
            正常工序报工
          </el-radio>
          <el-radio
            :value="'REWORK'"
            :disabled="!!context && !(context.allowedReportTypes || []).includes('REWORK')"
          >
            返工报工
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="工序" required>
        <WmsSelect v-model="form.processCode" filterable placeholder="请选择可执行工序">
          <el-option
            v-for="p in plans"
            :key="p.processCode"
            :label="`${p.processName} (${p.processCode}) 已报 ${p.reportedQty}/${p.planQty}`"
            :value="p.processCode"
          />
        </WmsSelect>
      </el-form-item>
      <el-form-item v-if="form.reportType === 'REWORK'" label="关联不良单">
        <WmsSelect v-model="form.defectNo" clearable placeholder="选择不良单">
          <el-option v-for="no in context?.openDefectNos || []" :key="no" :label="no" :value="no" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="数量" required>
        <el-input-number v-model="form.qty" :min="0.0001" :precision="4" />
        <span v-if="currentPlan()" class="hint">
          计划 {{ currentPlan()?.planQty }}，已报 {{ currentPlan()?.reportedQty }}
        </span>
      </el-form-item>
      <el-form-item label="重量(kg)">
        <el-input-number v-model="form.weightKg" :min="0" :precision="3" />
        <span class="hint">冲压称重可填，电子秤对接后续提供</span>
      </el-form-item>
      <el-form-item label="设备" required>
        <WmsSelect v-model="form.equipmentCode" filterable placeholder="选择设备">
          <el-option
            v-for="eq in equipment"
            :key="eq.equipmentCode"
            :label="`${eq.equipmentName} (${eq.equipmentCode})`"
            :value="eq.equipmentCode"
          />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交报工</el-button>
        <el-button @click="context = null">取消</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.hint {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
