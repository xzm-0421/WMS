<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMesRoutes, refreshMesRoutes, type MesRoute } from '@/api/mes'
import { useUserStore } from '@/stores/user'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<MesRoute[]>([])
const total = ref(0)
const query = reactive({ productCode: '', productName: '', current: 1, size: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await getMesRoutes(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  await ElMessageBox.confirm('将从金蝶ERP拉取工艺路线到本地，是否继续？', '从金蝶同步工艺路线')
  syncing.value = true
  try {
    const result = await refreshMesRoutes()
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
      title="工艺路线以金蝶ERP为权威源，同步后存储在本系统，页面只读不可改。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="产品编码">
        <el-input v-model="query.productCode" clearable />
      </el-form-item>
      <el-form-item label="产品名称">
        <el-input v-model="query.productName" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('mes:route:sync') || userStore.hasPermission('mes:master:sync')"
          type="success"
          :loading="syncing"
          @click="handleSync"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column label="路线编码" width="140">
        <template #default="{ row }">
          <RouterLink class="detail-link" :to="`/mes/routes/${row.id}`">{{ row.routeCode }}</RouterLink>
        </template>
      </el-table-column>
      <el-table-column prop="routeName" label="路线名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="productCode" label="物料编码" width="140" />
      <el-table-column prop="erpMaterialId" label="物料内码" width="110" />
      <el-table-column prop="productName" label="产品名称" min-width="180" />
      <el-table-column prop="versionNo" label="版本号" width="100" />
      <el-table-column label="同步" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </el-table-column>
      <el-table-column prop="lastSyncTime" label="最后同步" width="170" />
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

<style scoped>
.detail-link {
  color: var(--el-color-primary);
}
</style>
