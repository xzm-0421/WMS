<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMesResources,
  refreshMesResources,
  RESOURCE_TYPE_LABEL,
  type MesResource,
} from '@/api/mes'
import { useUserStore } from '@/stores/user'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<MesResource[]>([])
const total = ref(0)
const query = reactive({
  resourceCode: '',
  resourceName: '',
  resourceType: '',
  workCenterCode: '',
  status: undefined as number | undefined,
  current: 1,
  size: 20,
})

async function loadData() {
  loading.value = true
  try {
    const res = await getMesResources(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  await ElMessageBox.confirm('将从金蝶ERP拉取资源到本地，是否继续？', '从金蝶同步资源')
  syncing.value = true
  try {
    const result = await refreshMesResources()
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
      title="资源以金蝶ERP为权威源（类别含 设备/团队/人员），同步后本地只读。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="资源编码">
        <el-input v-model="query.resourceCode" clearable />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="query.resourceName" clearable />
      </el-form-item>
      <el-form-item label="类别">
        <WmsSelect v-model="query.resourceType" clearable placeholder="全部">
          <el-option label="设备" value="EQUIPMENT" />
          <el-option label="团队" value="TEAM" />
          <el-option label="人员" value="PERSONNEL" />
          <el-option label="其他" value="OTHER" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="工作中心">
        <el-input v-model="query.workCenterCode" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('mes:resource:sync') || userStore.hasPermission('mes:master:sync')"
          type="success"
          :loading="syncing"
          @click="handleSync"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column label="资源编码" width="150">
        <template #default="{ row }">
          <RouterLink class="detail-link" :to="`/mes/resources/${encodeURIComponent(row.resourceCode)}`">
            {{ row.resourceCode }}
          </RouterLink>
        </template>
      </el-table-column>
      <el-table-column prop="resourceName" label="名称" min-width="150" />
      <el-table-column label="类别" width="100">
        <template #default="{ row }">
          {{ RESOURCE_TYPE_LABEL[row.resourceType] || row.resourceTypeCode || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="工作中心" width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.workCenterName || row.workCenterCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="关联对象" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.refName || row.refCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="capacity" label="产能" width="90" />
      <el-table-column prop="unitCode" label="单位" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><WmsStatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="同步" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.syncStatus" pending-label="未同步" failed-label="同步失败" />
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

<style scoped>
.detail-link {
  color: var(--el-color-primary);
}
</style>
