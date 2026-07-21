<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getWarehouses,
  createWarehouse,
  updateWarehouse,
  deleteWarehouse,
  type Warehouse,
} from '@/api/warehouse'
import { syncKingdeeWarehouses } from '@/api/kingdeeMasterData'

const loading = ref(false)
const syncing = ref(false)
const tableData = ref<Warehouse[]>([])
const total = ref(0)
const query = reactive({ warehouseCode: '', warehouseName: '', current: 1, size: 20 })

const dialogVisible = ref(false)
const dialogTitle = ref('新增仓库')
const editingCode = ref<string | null>(null)
const form = reactive<Warehouse>({
  warehouseCode: '',
  warehouseName: '',
  warehouseType: 'RAW',
  erpWarehouseCode: '',
  address: '',
  phone: '',
  status: 1,
})

function resetForm() {
  Object.assign(form, {
    warehouseCode: '',
    warehouseName: '',
    warehouseType: 'RAW',
    erpWarehouseCode: '',
    address: '',
    phone: '',
    status: 1,
  })
  editingCode.value = null
}

async function loadData() {
  loading.value = true
  try {
    const res = await getWarehouses(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增仓库'
  dialogVisible.value = true
}

function handleEdit(row: Warehouse) {
  resetForm()
  editingCode.value = row.warehouseCode
  Object.assign(form, row)
  dialogTitle.value = '编辑仓库'
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.warehouseName?.trim()) {
    ElMessage.warning('请填写仓库名称')
    return
  }
  if (editingCode.value) {
    await updateWarehouse(editingCode.value, form)
    ElMessage.success('更新成功')
  } else {
    await createWarehouse({ ...form, warehouseCode: form.warehouseCode?.trim() || '' })
    ElMessage.success('创建成功（编码可自动生成）')
  }
  dialogVisible.value = false
  loadData()
}

async function handleSyncFromKingdee() {
  const keyword = query.warehouseCode?.trim() || query.warehouseName?.trim() || ''
  await ElMessageBox.confirm(
    keyword
      ? `将从金蝶拉取并更新匹配「${keyword}」的仓库到本地，是否继续？`
      : '将从金蝶拉取全部已审核仓库到本地，是否继续？',
    '从金蝶同步仓库',
  )
  syncing.value = true
  try {
    const result = await syncKingdeeWarehouses(keyword || undefined)
    ElMessage.success(result.message || '同步完成')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '同步失败')
  } finally {
    syncing.value = false
  }
}

async function handleDelete(row: Warehouse) {
  await ElMessageBox.confirm(`确定删除仓库 ${row.warehouseName}？`, '提示')
  await deleteWarehouse(row.warehouseCode)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="仓库编码">
        <el-input v-model="query.warehouseCode" clearable />
      </el-form-item>
      <el-form-item label="仓库名称">
        <el-input v-model="query.warehouseName" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="handleAdd">新增</el-button>
        <el-button type="success" :loading="syncing" @click="handleSyncFromKingdee">从金蝶同步</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="warehouseCode" label="仓库编码" width="120" />
      <el-table-column prop="warehouseName" label="仓库名称" min-width="160" />
      <el-table-column prop="erpWarehouseCode" label="金蝶仓库" width="120" />
      <el-table-column prop="warehouseType" label="类型" width="100">
        <template #default="{ row }">
          {{ row.warehouseType === 'RAW' ? '原料仓' : row.warehouseType === 'FINISHED' ? '成品仓' : row.warehouseType === 'AUX' ? '辅料仓' : row.warehouseType }}
        </template>
      </el-table-column>
      <el-table-column prop="address" label="地址" min-width="180" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <WmsStatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
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

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="仓库编码">
        <el-input
          v-model="form.warehouseCode"
          :disabled="!!editingCode"
          :placeholder="editingCode ? '' : '留空则系统自动生成'"
        />
      </el-form-item>
      <el-form-item label="仓库名称" required>
        <el-input v-model="form.warehouseName" />
      </el-form-item>
      <el-form-item label="金蝶仓库编码">
        <el-input v-model="form.erpWarehouseCode" placeholder="与金蝶 FStockId 一致，如 CK004" />
      </el-form-item>
      <el-form-item label="仓库类型">
        <WmsSelect v-model="form.warehouseType">
          <el-option label="原料仓" value="RAW" />
          <el-option label="成品仓" value="FINISHED" />
          <el-option label="辅料仓" value="AUX" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="地址">
        <el-input v-model="form.address" />
      </el-form-item>
      <el-form-item label="电话">
        <el-input v-model="form.phone" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">确定</el-button>
    </template>
  </el-dialog>
</template>
