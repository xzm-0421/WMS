<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMaterials, deleteMaterial, MATERIAL_TYPE_LABEL, type Material } from '@/api/material'
import { syncKingdeeMaterials } from '@/api/kingdeeMasterData'
import { useUserStore } from '@/stores/user'
import PrintActions from '@/components/PrintActions.vue'

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<Material[]>([])
const total = ref(0)
const query = reactive({ materialCode: '', materialName: '', current: 1, size: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await getMaterials(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSyncFromKingdee() {
  const keyword = query.materialCode?.trim() || query.materialName?.trim() || ''
  await ElMessageBox.confirm(
    keyword
      ? `将从金蝶拉取并更新匹配「${keyword}」的物料到本地，是否继续？`
      : '将从金蝶拉取全部已审核物料到本地（编码与金蝶一致），是否继续？',
    '从金蝶同步物料',
  )
  syncing.value = true
  try {
    const result = await syncKingdeeMaterials(keyword || undefined)
    ElMessage.success(result.message || '同步完成')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '同步失败')
  } finally {
    syncing.value = false
  }
}

async function handleDelete(row: Material) {
  await ElMessageBox.confirm(`确定删除物料 ${row.materialCode}？（逻辑删除）`, '提示')
  await deleteMaterial(row.materialCode)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="物料以金蝶 BD_MATERIAL 为权威源，本地只读；仅支持从金蝶同步与逻辑删除。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="物料编码">
        <el-input v-model="query.materialCode" clearable />
      </el-form-item>
      <el-form-item label="物料名称">
        <el-input v-model="query.materialName" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('base:material:sync')"
          type="success"
          :loading="syncing"
          @click="handleSyncFromKingdee"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="materialCode" label="物料编码" width="140" />
      <el-table-column prop="erpMaterialId" label="内码" width="110" />
      <el-table-column prop="materialName" label="物料名称" min-width="180" />
      <el-table-column prop="specification" label="规格" min-width="120" show-overflow-tooltip />
      <el-table-column prop="materialType" label="类型" width="100">
        <template #default="{ row }">
          {{ MATERIAL_TYPE_LABEL[row.materialType] || row.materialType }}
        </template>
      </el-table-column>
      <el-table-column prop="categoryCode" label="分类" width="100" />
      <el-table-column prop="unitCode" label="单位" width="80" />
      <el-table-column prop="barcodeRule" label="条码规则" width="120" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <WmsStatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <PrintActions biz="material_label" :doc-no="row.materialCode" />
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
