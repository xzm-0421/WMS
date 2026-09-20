<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMesMaterials, refreshMesMaterials, type MesMaterial } from '@/api/mes'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<MesMaterial[]>([])
const total = ref(0)
const query = reactive({
  materialCode: '',
  materialName: '',
  status: undefined as number | undefined,
  current: 1,
  size: 20,
})

async function loadData() {
  loading.value = true
  try {
    const res = await getMesMaterials(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  await ElMessageBox.confirm(
    '将通过金蝶 executeBillQuery（FormId=BD_MATERIAL）拉取物料并写入本地，页面只读。是否继续？',
    '从金蝶同步物料',
  )
  syncing.value = true
  try {
    const result = await refreshMesMaterials()
    ElMessage[result.success ? 'success' : 'warning'](result.message || '同步完成')
    await loadData()
  } finally {
    syncing.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="物料以金蝶 BD_MATERIAL 为权威源，经 executeBillQuery 同步后存储在本地，本页只读。"
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
          v-if="userStore.hasPermission('mes:material:sync') || userStore.hasPermission('mes:master:sync')"
          type="success"
          :loading="syncing"
          @click="handleSync"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="materialCode" label="物料编码" width="140" />
      <el-table-column prop="materialName" label="物料名称" min-width="180" />
      <el-table-column prop="specification" label="规格型号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="unitCode" label="单位" width="80" />
      <el-table-column prop="materialType" label="属性" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><WmsStatusTag :status="row.status" /></template>
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
