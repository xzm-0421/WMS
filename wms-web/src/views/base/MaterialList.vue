<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMaterials,
  createMaterial,
  updateMaterial,
  deleteMaterial,
  MATERIAL_TYPE_LABEL,
  type Material,
} from '@/api/material'
import { syncKingdeeMaterials } from '@/api/kingdeeMasterData'
import { getBarcodeRules, type BarcodeRule } from '@/api/barcode'
import PrintActions from '@/components/PrintActions.vue'

const loading = ref(false)
const syncing = ref(false)
const tableData = ref<Material[]>([])
const total = ref(0)
const query = reactive({ materialCode: '', materialName: '', current: 1, size: 20 })
const barcodeRules = ref<BarcodeRule[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增物料')
const editingCode = ref<string | null>(null)
const form = reactive<Material>({
  materialCode: '',
  materialName: '',
  categoryCode: 'CAT001',
  unitCode: 'KG',
  materialType: 'RAW',
  batchManaged: 1,
  barcodeRule: '',
  status: 1,
})

function resetForm() {
  Object.assign(form, {
    materialCode: '',
    materialName: '',
    categoryCode: 'CAT001',
    unitCode: 'KG',
    materialType: 'RAW',
    batchManaged: 1,
    barcodeRule: '',
    status: 1,
  })
  editingCode.value = null
}

async function loadBarcodeRules() {
  const res = await getBarcodeRules({ current: 1, size: 200, status: 1 })
  barcodeRules.value = res.records
}

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

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增物料'
  dialogVisible.value = true
}

function handleEdit(row: Material) {
  resetForm()
  editingCode.value = row.materialCode
  Object.assign(form, row)
  dialogTitle.value = '编辑物料'
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.materialName?.trim()) {
    ElMessage.warning('请填写物料名称')
    return
  }
  if (editingCode.value) {
    if (!form.materialCode?.trim()) {
      ElMessage.warning('物料编码不能为空')
      return
    }
    await updateMaterial(editingCode.value, form)
    ElMessage.success('更新成功')
  } else {
    await createMaterial({ ...form, materialCode: form.materialCode?.trim() || '' })
    ElMessage.success('创建成功（编码可自动生成）')
  }
  dialogVisible.value = false
  loadData()
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
  await ElMessageBox.confirm(`确定删除物料 ${row.materialCode}？`, '提示')
  await deleteMaterial(row.materialCode)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadBarcodeRules()
  loadData()
})
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="物料编码">
        <el-input v-model="query.materialCode" clearable />
      </el-form-item>
      <el-form-item label="物料名称">
        <el-input v-model="query.materialName" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="handleAdd">新增</el-button>
        <el-button type="success" :loading="syncing" @click="handleSyncFromKingdee">从金蝶同步</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="materialCode" label="物料编码" width="140" />
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
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
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

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" @closed="resetForm">
    <el-form :model="form" label-width="100px">
      <el-form-item label="物料编码">
        <el-input
          v-model="form.materialCode"
          :disabled="!!editingCode"
          :placeholder="editingCode ? '' : '留空则系统自动生成'"
        />
      </el-form-item>
      <el-form-item label="物料名称" required>
        <el-input v-model="form.materialName" />
      </el-form-item>
      <el-form-item label="分类编码">
        <el-input v-model="form.categoryCode" />
      </el-form-item>
      <el-form-item label="单位">
        <el-input v-model="form.unitCode" />
      </el-form-item>
      <el-form-item label="物料类型">
        <WmsSelect v-model="form.materialType" block>
          <el-option label="原材料" value="RAW" />
          <el-option label="成品" value="FINISHED" />
          <el-option label="辅料" value="AUX" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="批次管理">
        <el-switch v-model="form.batchManaged" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="条码规则">
        <WmsSelect v-model="form.barcodeRule" block clearable placeholder="不绑定则按全量规则匹配">
          <el-option
            v-for="rule in barcodeRules"
            :key="rule.ruleCode"
            :label="`${rule.ruleCode} - ${rule.ruleName}`"
            :value="rule.ruleCode"
          />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">确定</el-button>
    </template>
  </el-dialog>
</template>
