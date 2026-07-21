<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { getMaterials, MATERIAL_TYPE_LABEL, type Material } from '@/api/material'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'
import { resolveMaterialPicker, useMaterialPickerState } from '@/composables/useMaterialPicker'

const { visible } = useMaterialPickerState()

const loading = ref(false)
const tableData = ref<Material[]>([])
const total = ref(0)
const query = reactive({ materialCode: '', materialName: '', status: 1, current: 1, size: 15 })

async function loadData() {
  loading.value = true
  try {
    const res = await getMaterials({ ...query })
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSelect(row: Material) {
  resolveMaterialPicker(row)
}

function handleClose() {
  resolveMaterialPicker(null)
}

watch(visible, (open) => {
  if (open) {
    query.materialCode = ''
    query.materialName = ''
    query.current = 1
    loadData()
  }
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="选择物料"
    width="860px"
    destroy-on-close
    append-to-body
    @close="handleClose"
  >
    <p class="picker-tip">双击行选中物料，自动带出编码、名称、单位等信息</p>
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="物料编码">
        <el-input v-model="query.materialCode" clearable placeholder="编码" @keyup.enter="loadData" />
      </el-form-item>
      <el-form-item label="物料名称">
        <el-input v-model="query.materialName" clearable placeholder="名称" @keyup.enter="loadData" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="tableData"
      stripe
      highlight-current-row
      height="360"
      class="picker-table"
      @row-dblclick="handleSelect"
    >
      <el-table-column prop="materialCode" label="物料编码" width="140" />
      <el-table-column prop="materialName" label="物料名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="materialType" label="类型" width="90">
        <template #default="{ row }">
          {{ MATERIAL_TYPE_LABEL[row.materialType] ?? row.materialType }}
        </template>
      </el-table-column>
      <el-table-column prop="unitCode" label="单位" width="70" />
      <el-table-column prop="categoryCode" label="分类" width="100" />
      <el-table-column prop="status" label="状态" width="70">
        <template #default="{ row }">
          <OrderStatusTag :status="row.status" />
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      class="picker-pagination"
      @current-change="loadData"
    />
  </el-dialog>
</template>

<style scoped>
.picker-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.search-form {
  margin-bottom: 8px;
}
.picker-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
