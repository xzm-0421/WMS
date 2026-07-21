<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBusinessRules, updateBusinessRule } from '@/api/inventoryExt'

const rules = ref<{ ruleCode: string; ruleName: string; ruleValue: string; remark?: string }[]>([])

async function loadData() {
  rules.value = await getBusinessRules()
}

async function save(row: { ruleCode: string; ruleValue: string }) {
  await updateBusinessRule(row.ruleCode, row.ruleValue)
  ElMessage.success('规则已更新')
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-table :data="rules" stripe>
      <el-table-column prop="ruleCode" label="规则编码" width="200" />
      <el-table-column prop="ruleName" label="规则名称" width="180" />
      <el-table-column label="规则值" width="160">
        <template #default="{ row }">
          <el-input v-model="row.ruleValue" size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="240" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="save(row)">保存</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
