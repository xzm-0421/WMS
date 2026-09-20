<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMesReportContext, submitMesTransfer, type MesOpPlan } from '@/api/mes'

const loading = ref(false)
const submitting = ref(false)
const plans = ref<MesOpPlan[]>([])
const form = reactive({
  moNo: '',
  fromProcessCode: '',
  toProcessCode: '',
  qty: 1,
  remark: '',
})

async function loadContext() {
  if (!form.moNo.trim()) {
    ElMessage.warning('请输入工单号')
    return
  }
  loading.value = true
  try {
    const ctx = await getMesReportContext(form.moNo.trim())
    plans.value = ctx.plans || []
    if (plans.value.length) {
      form.fromProcessCode = plans.value[0].processCode || ''
      form.toProcessCode = plans.value[1]?.processCode || ''
    }
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  submitting.value = true
  try {
    const res = await submitMesTransfer({ ...form, moNo: form.moNo.trim() })
    ElMessage.success(`转移成功 ${res.transferNo}`)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="同工作中心在报工成功后会自动转移；跨车间请在此手工提交。PDA 扫码转移后续再做。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :model="form" label-width="110px" style="max-width: 640px">
      <el-form-item label="工单号" required>
        <el-input v-model="form.moNo" @keyup.enter="loadContext">
          <template #append>
            <el-button :loading="loading" @click="loadContext">查询</el-button>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="源工序" required>
        <WmsSelect v-model="form.fromProcessCode" filterable>
          <el-option
            v-for="p in plans"
            :key="p.processCode"
            :label="`${p.processName} (${p.processCode})`"
            :value="p.processCode"
          />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="目标工序" required>
        <WmsSelect v-model="form.toProcessCode" filterable>
          <el-option
            v-for="p in plans"
            :key="'to-' + p.processCode"
            :label="`${p.processName} (${p.processCode})`"
            :value="p.processCode"
          />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="数量" required>
        <el-input-number v-model="form.qty" :min="0.0001" :precision="4" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交转移</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>
