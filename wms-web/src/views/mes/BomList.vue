<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMesBom, getMesBoms, refreshMesBoms, type MesBomDetail, type MesBomHeader } from '@/api/mes'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<MesBomHeader[]>([])
const total = ref(0)
const query = reactive({ bomCode: '', productCode: '', current: 1, size: 20 })
const detailVisible = ref(false)
const current = ref<MesBomHeader | null>(null)
const details = ref<MesBomDetail[]>([])

async function loadData() {
  loading.value = true
  try {
    const res = await getMesBoms(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  await ElMessageBox.confirm(
    '将通过金蝶 executeBillQuery（FormId=ENG_BOM）拉取物料清单并写入本地，页面只读。是否继续？',
    '从金蝶同步 BOM',
  )
  syncing.value = true
  try {
    const result = await refreshMesBoms()
    ElMessage[result.success ? 'success' : 'warning'](result.message || '同步完成')
    await loadData()
  } finally {
    syncing.value = false
  }
}

async function showDetail(row: MesBomHeader) {
  const vo = await getMesBom(row.bomCode!)
  current.value = vo.header
  details.value = vo.details || []
  detailVisible.value = true
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="BOM 以金蝶 ENG_BOM 为权威源，经 executeBillQuery 同步后存储在本地，本页只读。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="BOM编码">
        <el-input v-model="query.bomCode" clearable />
      </el-form-item>
      <el-form-item label="父项物料">
        <el-input v-model="query.productCode" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('mes:bom:sync') || userStore.hasPermission('mes:master:sync')"
          type="success"
          :loading="syncing"
          @click="handleSync"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="bomCode" label="BOM编码" width="160" />
      <el-table-column prop="productCode" label="父项物料" width="140" />
      <el-table-column prop="versionNo" label="版本" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><WmsStatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">查看明细</el-button>
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

  <el-dialog v-model="detailVisible" title="BOM明细" width="720px">
    <el-descriptions v-if="current" :column="2" border>
      <el-descriptions-item label="BOM编码">{{ current.bomCode }}</el-descriptions-item>
      <el-descriptions-item label="父项物料">{{ current.productCode }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ current.versionNo }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="details" stripe style="margin-top: 12px">
      <el-table-column prop="lineNo" label="#" width="60" />
      <el-table-column prop="materialCode" label="子项物料" width="140" />
      <el-table-column prop="materialName" label="名称" min-width="160" />
      <el-table-column prop="unitCode" label="单位" width="80" />
      <el-table-column prop="qtyPer" label="用量" width="100" />
    </el-table>
  </el-dialog>
</template>
