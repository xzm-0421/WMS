<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import {
  createLabelPrintJob,
  listLabelPrintJobs,
  updateLabelPrintSettings,
  type LabelPrintJob,
  type LabelPrintCreateRequest,
} from '@/api/labelPrint'
import MaterialSelectInput from '@/components/MaterialSelectInput.vue'
import { printKingdeeLabelDirect, printKingdeeLabelsBatch } from '@/utils/labelPrint'
import { DEFAULT_LABEL_PAPER, LABEL_PAPER_PRESETS } from '@/config/printConfig'

const loading = ref(false)
const tableData = ref<LabelPrintJob[]>([])
const total = ref(0)
const query = ref({ current: 1, size: 20, keyword: '', status: '' })
const tableRef = ref<TableInstance>()
const selectedRows = ref<LabelPrintJob[]>([])
const batchPrinting = ref(false)

const createVisible = ref(false)
const settingsVisible = ref(false)
const saving = ref(false)
const currentJob = ref<LabelPrintJob | null>(null)

const createForm = reactive<LabelPrintCreateRequest>({
  materialCode: '',
  batchNo: '',
  productionDate: '',
  quantity: undefined,
  barcodeType: 'QR',
  labelWidthMm: DEFAULT_LABEL_PAPER.width,
  labelHeightMm: DEFAULT_LABEL_PAPER.height,
  copies: 1,
})

const settingsForm = reactive({
  labelWidthMm: DEFAULT_LABEL_PAPER.width,
  labelHeightMm: DEFAULT_LABEL_PAPER.height,
  copies: 1,
  barcodeType: 'QR',
})

async function load() {
  loading.value = true
  try {
    const res = await listLabelPrintJobs(query.value)
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function resetCreateForm() {
  Object.assign(createForm, {
    materialCode: '',
    batchNo: '',
    productionDate: '',
    quantity: undefined,
    barcodeType: 'QR',
    labelWidthMm: DEFAULT_LABEL_PAPER.width,
    labelHeightMm: DEFAULT_LABEL_PAPER.height,
    copies: 1,
  })
}

function applyPaperPreset(target: { labelWidthMm: number; labelHeightMm: number }, preset: typeof LABEL_PAPER_PRESETS[number]) {
  target.labelWidthMm = preset.width
  target.labelHeightMm = preset.height
}

function openCreate() {
  resetCreateForm()
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.materialCode?.trim()) {
    ElMessage.warning('请填写物料编码')
    return
  }
  saving.value = true
  try {
    const job = await createLabelPrintJob(createForm)
    ElMessage.success('打印任务已创建')
    createVisible.value = false
    await load()
    await handlePrint(job)
  } finally {
    saving.value = false
  }
}

function openSettings(row: LabelPrintJob) {
  currentJob.value = row
  settingsForm.labelWidthMm = Number(row.labelWidthMm) || DEFAULT_LABEL_PAPER.width
  settingsForm.labelHeightMm = Number(row.labelHeightMm) || DEFAULT_LABEL_PAPER.height
  settingsForm.copies = row.copies || 1
  settingsForm.barcodeType = row.barcodeType || 'QR'
  settingsVisible.value = true
}

async function submitSettings() {
  if (!currentJob.value) return
  saving.value = true
  try {
    await updateLabelPrintSettings(currentJob.value.jobId, { ...settingsForm })
    ElMessage.success('打印参数已保存')
    settingsVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handlePreview(row: LabelPrintJob) {
  try {
    await printKingdeeLabelDirect(row, { preview: true })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '预览失败')
  }
}

async function handlePrint(row: LabelPrintJob) {
  try {
    await printKingdeeLabelDirect(row)
    ElMessage.success('已发送打印')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '打印失败')
  }
}

function handleSelectionChange(rows: LabelPrintJob[]) {
  selectedRows.value = rows
}

function clearSelection() {
  tableRef.value?.clearSelection()
  selectedRows.value = []
}

async function handleBatchPreview() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要预览的打印任务')
    return
  }
  batchPrinting.value = true
  try {
    await printKingdeeLabelsBatch(selectedRows.value, { preview: true })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量预览失败')
  } finally {
    batchPrinting.value = false
  }
}

async function handleBatchPrint() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要打印的任务')
    return
  }
  batchPrinting.value = true
  try {
    await printKingdeeLabelsBatch(selectedRows.value)
    ElMessage.success(`已发送 ${selectedRows.value.length} 条任务打印`)
    clearSelection()
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量打印失败')
  } finally {
    batchPrinting.value = false
  }
}

function selectKingdeeRowsOnPage() {
  const rows = tableData.value.filter((row) => row.sourceType === 'KINGDEE')
  if (!rows.length) {
    ElMessage.warning('当前页没有金蝶来源的打印任务')
    return
  }
  rows.forEach((row) => tableRef.value?.toggleRowSelection(row, true))
  ElMessage.success(`已选中当前页 ${rows.length} 条金蝶任务`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>金蝶物料标签打印</span>
          <div class="header-actions">
            <el-button
              :disabled="!selectedRows.length"
              :loading="batchPrinting"
              @click="handleBatchPreview"
            >
              批量预览{{ selectedRows.length ? ` (${selectedRows.length})` : '' }}
            </el-button>
            <el-button
              type="primary"
              :disabled="!selectedRows.length"
              :loading="batchPrinting"
              @click="handleBatchPrint"
            >
              批量打印{{ selectedRows.length ? ` (${selectedRows.length})` : '' }}
            </el-button>
            <el-button type="primary" @click="openCreate">新建打印任务</el-button>
          </div>
        </div>
      </template>

      <el-alert
        class="mb-12"
        type="info"
        :closable="false"
        title="标签打印机说明"
        description="物料名称/规格/单位均来自「物料信息」主数据。新建时只需选择物料并填写批次、数量等打印参数。"
      />

      <el-form :inline="true" class="mb-12">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="物料编码/名称"
            style="width: 220px"
            @keyup.enter="load"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="待打印" value="PENDING" />
            <el-option label="已预览" value="OPENED" />
            <el-option label="已打印" value="PRINTED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="selectKingdeeRowsOnPage">选中当前页金蝶任务</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="tableData"
        border
        row-key="jobId"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" reserve-selection />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <WmsStatusTag :status="row.sourceType" />
          </template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料编码" min-width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批次号" width="110" />
        <el-table-column prop="productionDate" label="生产日期" width="110">
          <template #default="{ row }">
            <WmsDateText :value="row.productionDate" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="90">
          <template #default="{ row }">
            {{ row.quantity ?? '-' }}{{ row.unitCode ? ` ${row.unitCode}` : '' }}
          </template>
        </el-table-column>
        <el-table-column label="标签尺寸" width="100">
          <template #default="{ row }">
            {{ row.labelWidthMm }}×{{ row.labelHeightMm }}mm
          </template>
        </el-table-column>
        <el-table-column prop="copies" label="份数" width="60" />
        <el-table-column prop="operatorName" label="操作人" width="90" />
        <el-table-column prop="createTime" label="创建时间" min-width="120">
          <template #default="{ row }">
            <WmsDateText :value="row.createTime" />
          </template>
        </el-table-column>
        <el-table-column prop="printedTime" label="打印时间" min-width="120">
          <template #default="{ row }">
            <WmsDateText :value="row.printedTime" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" fixed="right">
          <template #default="{ row }">
            <WmsStatusTag :status="row.status" pending-label="待打印" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
            <el-button link type="primary" @click="handlePrint(row)">打印</el-button>
            <el-button link type="primary" @click="openSettings(row)">参数</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-16"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="新建物料标签打印" width="520px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="物料" required>
          <MaterialSelectInput
            v-model="createForm.materialCode"
            placeholder="从物料信息中选择"
          />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="createForm.batchNo" />
        </el-form-item>
        <el-form-item label="生产日期">
          <el-date-picker
            v-model="createForm.productionDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选填，默认当天"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="createForm.quantity" :min="0" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="条码类型">
          <el-select v-model="createForm.barcodeType" style="width: 100%">
            <el-option label="二维码 QR" value="QR" />
            <el-option label="Code128" value="CODE128" />
            <el-option label="Code39" value="CODE39" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签纸规格">
          <el-button-group>
            <el-button
              v-for="preset in LABEL_PAPER_PRESETS"
              :key="preset.label"
              size="small"
              :type="createForm.labelWidthMm === preset.width && createForm.labelHeightMm === preset.height ? 'primary' : 'default'"
              @click="applyPaperPreset(createForm, preset)"
            >
              {{ preset.label }}
            </el-button>
          </el-button-group>
        </el-form-item>
        <el-form-item label="纸张宽(mm)">
          <el-input-number v-model="createForm.labelWidthMm" :min="40" :max="300" style="width: 100%" />
        </el-form-item>
        <el-form-item label="纸张高(mm)">
          <el-input-number v-model="createForm.labelHeightMm" :min="30" :max="300" style="width: 100%" />
        </el-form-item>
        <el-form-item label="打印份数">
          <el-input-number v-model="createForm.copies" :min="1" :max="99" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建并预览</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="settingsVisible" title="打印参数配置" width="420px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="标签纸规格">
          <el-button-group>
            <el-button
              v-for="preset in LABEL_PAPER_PRESETS"
              :key="preset.label"
              size="small"
              :type="settingsForm.labelWidthMm === preset.width && settingsForm.labelHeightMm === preset.height ? 'primary' : 'default'"
              @click="applyPaperPreset(settingsForm, preset)"
            >
              {{ preset.label }}
            </el-button>
          </el-button-group>
        </el-form-item>
        <el-form-item label="纸张宽(mm)">
          <el-input-number v-model="settingsForm.labelWidthMm" :min="40" :max="300" style="width: 100%" />
        </el-form-item>
        <el-form-item label="纸张高(mm)">
          <el-input-number v-model="settingsForm.labelHeightMm" :min="30" :max="300" style="width: 100%" />
        </el-form-item>
        <el-form-item label="打印份数">
          <el-input-number v-model="settingsForm.copies" :min="1" :max="99" style="width: 100%" />
        </el-form-item>
        <el-form-item label="条码类型">
          <el-select v-model="settingsForm.barcodeType" style="width: 100%">
            <el-option label="二维码 QR" value="QR" />
            <el-option label="Code128" value="CODE128" />
            <el-option label="Code39" value="CODE39" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSettings">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.mb-12 { margin-bottom: 12px; }
.mt-16 { margin-top: 16px; justify-content: flex-end; }
</style>
