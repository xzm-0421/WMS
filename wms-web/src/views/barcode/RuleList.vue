<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowUp, Delete, Plus } from '@element-plus/icons-vue'
import {
  getBarcodeRules,
  createBarcodeRule,
  updateBarcodeRule,
  updateBarcodeRuleStatus,
  deleteBarcodeRule,
  getBarcodeRuleVersions,
  parseBarcode,
  generateBarcode,
  traceBarcode,
  getBarcodeArchives,
  reprintBarcodeArchive,
  cleanupBarcodeArchives,
  getKingdeeMaterials,
  getKingdeeBatches,
  getKingdeeSerials,
  buildTemplateSegments,
  extractRuleFieldSources,
  TEMPLATE_OPTIONS,
  SEGMENT_FIELD_OPTIONS,
  type BarcodeRule,
  type BarcodeRuleVersion,
  type BarcodeSegment,
  type BarcodeGenerateResult,
  type BarcodeParseResult,
  type BarcodeTraceResult,
  type BarcodeArchive,
  type BarcodeGenerateRequest,
  type KingdeeMaterial,
  type KingdeeBatch,
  type KingdeeSerial,
} from '@/api/barcode'
import { openWmsPrint } from '@/utils/print'
import { formatDate } from '@/utils/format'

const activeTab = ref('rules')
const loading = ref(false)
const tableData = ref<BarcodeRule[]>([])
const total = ref(0)
const query = reactive({ ruleCode: '', ruleName: '', status: undefined as number | undefined, current: 1, size: 20 })

const dialogVisible = ref(false)
const dialogTitle = ref('新增条码规则')
const editingCode = ref<string | null>(null)
const changeLog = ref('')
const segments = ref<BarcodeSegment[]>([])
const form = reactive<BarcodeRule>({
  ruleCode: '',
  ruleName: '',
  appliesTo: 'MATERIAL',
  barcodeType: 'QR',
  templateCode: '',
  separator: '-',
  segmentsJson: '[]',
  description: '',
  status: 1,
  versionNo: 1,
})

const versionDrawerVisible = ref(false)
const versionList = ref<BarcodeRuleVersion[]>([])
const versionRuleCode = ref('')

const parseVisible = ref(false)
const parseRuleCode = ref('')
const parseInput = ref('')
const parseLoading = ref(false)
const parseResult = ref<BarcodeParseResult | null>(null)

const generateForm = reactive({
  ruleCode: '',
  materialCode: '',
  batchNo: '',
  packBarcode: '',
  serialNo: '',
  autoSerial: true,
  labelWidthMm: 100,
  labelHeightMm: 55,
})
const generateLoading = ref(false)
const generateResult = ref<BarcodeGenerateResult | null>(null)

const archiveLoading = ref(false)
const archiveData = ref<BarcodeArchive[]>([])
const archiveTotal = ref(0)
const archiveQuery = reactive({
  archiveNo: '',
  barcodeContent: '',
  materialCode: '',
  createTimeFrom: '',
  createTimeTo: '',
  current: 1,
  size: 20,
})
const archiveDateRange = ref<[string, string] | null>(null)

const traceInput = ref('')
const traceLoading = ref(false)
const traceResult = ref<BarcodeTraceResult | null>(null)

const kingdeeMaterialVisible = ref(false)
const kingdeeMaterialLoading = ref(false)
const kingdeeMaterials = ref<KingdeeMaterial[]>([])
const kingdeeMaterialKeyword = ref('')
const kingdeePickTarget = ref<'generate' | 'form'>('generate')

const kingdeeBatchVisible = ref(false)
const kingdeeBatchLoading = ref(false)
const kingdeeBatches = ref<KingdeeBatch[]>([])

const kingdeeSerialVisible = ref(false)
const kingdeeSerialLoading = ref(false)
const kingdeeSerials = ref<KingdeeSerial[]>([])

const selectedGenerateRule = computed(() =>
  tableData.value.find((r) => r.ruleCode === generateForm.ruleCode),
)

const generateRequiredFields = computed(() => new Set(extractRuleFieldSources(selectedGenerateRule.value)))

const generateNeedsMaterial = computed(() => generateRequiredFields.value.has('MATERIAL_CODE'))
const generateNeedsBatch = computed(() => generateRequiredFields.value.has('BATCH_NO'))
const generateNeedsPackBarcode = computed(() => generateRequiredFields.value.has('PACK_BARCODE'))
const generateNeedsSerial = computed(() => generateRequiredFields.value.has('SERIAL_NO'))

const generateAlertTitle = computed(() => {
  if (!generateForm.ruleCode) {
    return '请先选择条码规则，系统将根据规则分段仅展示所需的金蝶数据源。'
  }
  const labels: string[] = []
  if (generateNeedsMaterial.value) labels.push('物料')
  if (generateNeedsBatch.value) labels.push('批号')
  if (generateNeedsPackBarcode.value) labels.push('包装条码')
  if (generateNeedsSerial.value) labels.push('序列号')
  if (!labels.length) {
    return '当前规则未配置字段分段（仅固定值/日期等），请直接生成或编辑规则。'
  }
  return `当前规则需要：${labels.join('、')}。请从金蝶选取或手工录入对应数据后生成条码。`
})

watch(() => generateForm.ruleCode, () => {
  if (!generateNeedsMaterial.value) generateForm.materialCode = ''
  if (!generateNeedsBatch.value) generateForm.batchNo = ''
  if (!generateNeedsPackBarcode.value) generateForm.packBarcode = ''
  if (!generateNeedsSerial.value) {
    generateForm.serialNo = ''
    generateForm.autoSerial = true
  }
})

const previewBarcode = computed(() => {
  const ctx: Record<string, string> = {
    MATERIAL_CODE: generateForm.materialCode || 'MAT001',
    BATCH_NO: generateForm.batchNo || 'B20260101',
    PACK_BARCODE: generateForm.packBarcode || 'PKG001',
    SERIAL_NO: generateForm.autoSerial ? 'SN00000001' : (generateForm.serialNo || 'SN00000001'),
  }
  return assemblePreview(segments.value.length ? segments.value : parseSegments(form.segmentsJson), form.separator || '', ctx)
})

function parseSegments(json?: string): BarcodeSegment[] {
  if (!json) return []
  try {
    return JSON.parse(json) as BarcodeSegment[]
  } catch {
    return []
  }
}

function syncSegmentsJson() {
  form.segmentsJson = JSON.stringify(segments.value)
}

function assemblePreview(segs: BarcodeSegment[], separator: string, ctx: Record<string, string>) {
  return segs.map((seg) => {
    if (seg.type === 'SEPARATOR') return seg.value ?? separator
    if (seg.type === 'FIXED') return seg.value ?? ''
    if (seg.type === 'DATE') return new Date().toISOString().slice(0, 10).replace(/-/g, '')
    const key = seg.source || ''
    return ctx[key] || `{${SEGMENT_FIELD_OPTIONS.find((o) => o.source === key)?.label || key}}`
  }).join('')
}

function resetForm() {
  Object.assign(form, {
    ruleCode: '',
    ruleName: '',
    appliesTo: 'MATERIAL',
    barcodeType: 'QR',
    templateCode: '',
    separator: '-',
    segmentsJson: '[]',
    description: '',
    status: 1,
    versionNo: 1,
  })
  segments.value = []
  editingCode.value = null
  changeLog.value = ''
}

watch(() => form.templateCode, (code) => {
  if (code && code !== 'CUSTOM') {
    segments.value = buildTemplateSegments(code, form.separator || '')
    syncSegmentsJson()
  }
})

watch(() => form.separator, (sep) => {
  if (form.templateCode && form.templateCode !== 'CUSTOM') {
    segments.value = buildTemplateSegments(form.templateCode, sep || '')
    syncSegmentsJson()
  }
})

async function loadData() {
  loading.value = true
  try {
    const res = await getBarcodeRules(query)
    tableData.value = res.records
    total.value = res.total
    if (!generateForm.ruleCode && res.records.length > 0) {
      generateForm.ruleCode = res.records.find((r) => r.status === 1)?.ruleCode || res.records[0]!.ruleCode
    }
    if (!parseRuleCode.value && res.records.length > 0) {
      parseRuleCode.value = res.records[0]!.ruleCode
    }
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增条码规则'
  dialogVisible.value = true
}

function handleEdit(row: BarcodeRule) {
  resetForm()
  editingCode.value = row.ruleCode
  Object.assign(form, row)
  segments.value = parseSegments(row.segmentsJson)
  if (!segments.value.length && row.templateCode) {
    segments.value = buildTemplateSegments(row.templateCode, row.separator || '')
  }
  dialogTitle.value = `编辑规则（当前 v${row.versionNo || 1}）`
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.ruleName?.trim()) {
    ElMessage.warning('请填写规则名称')
    return
  }
  syncSegmentsJson()
  if (editingCode.value) {
    await updateBarcodeRule(editingCode.value, { ...form }, changeLog.value || undefined)
    ElMessage.success('更新成功，已生成新版本')
  } else {
    await createBarcodeRule({ ...form, ruleCode: form.ruleCode?.trim() || '' })
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  loadData()
}

async function handleToggleStatus(row: BarcodeRule) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '禁用'
  await ElMessageBox.confirm(`确定${action}规则「${row.ruleName}」？`, '提示')
  await updateBarcodeRuleStatus(row.ruleCode, next, `${action}规则`)
  ElMessage.success(`已${action}`)
  loadData()
}

async function handleDelete(row: BarcodeRule) {
  await ElMessageBox.confirm(`确定删除规则 ${row.ruleName}？`, '提示')
  await deleteBarcodeRule(row.ruleCode)
  ElMessage.success('删除成功')
  loadData()
}

async function showVersions(row: BarcodeRule) {
  versionRuleCode.value = row.ruleCode
  versionList.value = await getBarcodeRuleVersions(row.ruleCode)
  versionDrawerVisible.value = true
}

function addSegment(type: BarcodeSegment['type']) {
  if (type === 'FIELD') {
    segments.value.push({ type: 'FIELD', source: 'MATERIAL_CODE', label: '物料编码' })
  } else if (type === 'SEPARATOR') {
    segments.value.push({ type: 'SEPARATOR', value: form.separator || '-' })
  } else if (type === 'FIXED') {
    segments.value.push({ type: 'FIXED', value: '' })
  } else {
    segments.value.push({ type: 'DATE' })
  }
  form.templateCode = 'CUSTOM'
  syncSegmentsJson()
}

function removeSegment(index: number) {
  segments.value.splice(index, 1)
  form.templateCode = 'CUSTOM'
  syncSegmentsJson()
}

function moveSegment(index: number, dir: -1 | 1) {
  const target = index + dir
  if (target < 0 || target >= segments.value.length) return
  const tmp = segments.value[index]!
  segments.value[index] = segments.value[target]!
  segments.value[target] = tmp
  form.templateCode = 'CUSTOM'
  syncSegmentsJson()
}

function openParseTest() {
  parseInput.value = ''
  parseResult.value = null
  parseVisible.value = true
}

async function handleParse() {
  if (!parseRuleCode.value || !parseInput.value) {
    ElMessage.warning('请选择规则并输入条码')
    return
  }
  parseLoading.value = true
  try {
    parseResult.value = await parseBarcode(parseRuleCode.value, parseInput.value)
  } finally {
    parseLoading.value = false
  }
}

async function handleGenerate() {
  if (!generateForm.ruleCode) {
    ElMessage.warning('请选择条码规则')
    return
  }
  generateLoading.value = true
  try {
    generateResult.value = await generateBarcode(buildGeneratePayload())
    ElMessage.success('条码生成成功，已自动存档')
    if (activeTab.value === 'archive') {
      loadArchives()
    }
  } finally {
    generateLoading.value = false
  }
}

function buildGeneratePayload(): BarcodeGenerateRequest {
  const payload: BarcodeGenerateRequest = {
    ruleCode: generateForm.ruleCode,
    labelWidthMm: generateForm.labelWidthMm,
    labelHeightMm: generateForm.labelHeightMm,
  }
  if (generateNeedsMaterial.value) payload.materialCode = generateForm.materialCode
  if (generateNeedsBatch.value) payload.batchNo = generateForm.batchNo
  if (generateNeedsPackBarcode.value) payload.packBarcode = generateForm.packBarcode
  if (generateNeedsSerial.value) {
    payload.autoSerial = generateForm.autoSerial
    if (!generateForm.autoSerial) payload.serialNo = generateForm.serialNo
  }
  return payload
}

async function loadArchives() {
  archiveLoading.value = true
  try {
    const params: Record<string, unknown> = { ...archiveQuery }
    if (archiveDateRange.value?.[0]) {
      params.createTimeFrom = `${archiveDateRange.value[0]} 00:00:00`
    }
    if (archiveDateRange.value?.[1]) {
      params.createTimeTo = `${archiveDateRange.value[1]} 23:59:59`
    }
    const res = await getBarcodeArchives(params)
    archiveData.value = res.records
    archiveTotal.value = res.total
  } finally {
    archiveLoading.value = false
  }
}

async function handleArchiveReprint(row: BarcodeArchive) {
  await reprintBarcodeArchive(row.archiveNo)
  openWmsPrint('barcode_archive', row.archiveNo)
  ElMessage.success('已打开补打预览')
  loadArchives()
}

function handleArchivePrint(row: BarcodeArchive) {
  openWmsPrint('barcode_archive', row.archiveNo)
}

function handleArchiveDirectPrint(row: BarcodeArchive) {
  openWmsPrint('barcode_archive', row.archiveNo, { autoPrint: true })
}

async function handleArchiveCleanup() {
  await ElMessageBox.confirm('将按保留策略清理过期存档记录，是否继续？', '清理确认')
  const res = await cleanupBarcodeArchives()
  ElMessage.success(`已清理 ${res.removed} 条存档`)
  loadArchives()
}

function printGenerateResult() {
  if (generateResult.value?.archiveNo) {
    openWmsPrint('barcode_archive', generateResult.value.archiveNo)
  }
}

async function handleTrace() {
  if (!traceInput.value.trim()) {
    ElMessage.warning('请输入条码')
    return
  }
  traceLoading.value = true
  try {
    traceResult.value = await traceBarcode(traceInput.value.trim())
  } finally {
    traceLoading.value = false
  }
}

async function openKingdeeMaterials(target: 'generate' | 'form') {
  if (target === 'generate' && !generateNeedsMaterial.value) return
  kingdeePickTarget.value = target
  kingdeeMaterialKeyword.value = ''
  kingdeeMaterialVisible.value = true
  await loadKingdeeMaterials()
}

async function loadKingdeeMaterials() {
  kingdeeMaterialLoading.value = true
  try {
    const res = await getKingdeeMaterials({ keyword: kingdeeMaterialKeyword.value, current: 1, size: 50 })
    kingdeeMaterials.value = res.records
  } finally {
    kingdeeMaterialLoading.value = false
  }
}

function pickMaterial(row: KingdeeMaterial) {
  if (kingdeePickTarget.value === 'generate') {
    if (generateNeedsMaterial.value) {
      generateForm.materialCode = row.materialCode
    }
    if (generateNeedsPackBarcode.value) {
      generateForm.packBarcode = row.packBarCode || row.barCode || ''
    }
  }
  kingdeeMaterialVisible.value = false
}

async function openKingdeeBatches() {
  if (!generateNeedsBatch.value) return
  kingdeeBatchVisible.value = true
  kingdeeBatchLoading.value = true
  try {
    const res = await getKingdeeBatches({ materialCode: generateForm.materialCode, current: 1, size: 50 })
    kingdeeBatches.value = res.records
  } finally {
    kingdeeBatchLoading.value = false
  }
}

function pickBatch(row: KingdeeBatch) {
  generateForm.batchNo = row.batchNo
  kingdeeBatchVisible.value = false
}

async function openKingdeeSerials() {
  if (!generateNeedsSerial.value || generateForm.autoSerial) return
  kingdeeSerialVisible.value = true
  kingdeeSerialLoading.value = true
  try {
    const res = await getKingdeeSerials({
      materialCode: generateForm.materialCode,
      batchNo: generateForm.batchNo,
      current: 1,
      size: 50,
    })
    kingdeeSerials.value = res.records
  } finally {
    kingdeeSerialLoading.value = false
  }
}

function pickSerial(row: KingdeeSerial) {
  generateForm.serialNo = row.serialNo
  generateForm.autoSerial = false
  kingdeeSerialVisible.value = false
}

onMounted(() => {
  resetForm()
  loadData()
  loadArchives()
})
</script>

<template>
  <el-card shadow="never">
    <el-tabs v-model="activeTab">
      <!-- 规则管理 -->
      <el-tab-pane label="规则管理" name="rules">
        <el-form :inline="true" :model="query" class="mb-12">
          <el-form-item label="规则编码"><el-input v-model="query.ruleCode" clearable /></el-form-item>
          <el-form-item label="规则名称"><el-input v-model="query.ruleName" clearable /></el-form-item>
          <el-form-item label="状态">
            <WmsSelect v-model="query.status" clearable placeholder="全部">
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </WmsSelect>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadData">查询</el-button>
            <el-button @click="handleAdd">新增规则</el-button>
            <el-button @click="openParseTest">解析测试</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="loading" :data="tableData" stripe>
          <el-table-column prop="ruleCode" label="规则编码" width="130" />
          <el-table-column prop="ruleName" label="规则名称" min-width="140" />
          <el-table-column prop="templateCode" label="拼接模板" width="160">
            <template #default="{ row }">
              {{ TEMPLATE_OPTIONS.find((t) => t.code === row.templateCode)?.label || row.templateCode || '自定义' }}
            </template>
          </el-table-column>
          <el-table-column prop="versionNo" label="版本" width="70" align="center" />
          <el-table-column prop="barcodeType" label="条码类型" width="100" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <WmsStatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="showVersions(row)">版本</el-button>
              <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
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
      </el-tab-pane>

      <!-- 条码生成 -->
      <el-tab-pane label="条码生成" name="generate">
        <el-alert
          :title="generateAlertTitle"
          type="info"
          :closable="false"
          show-icon
          class="mb-16"
        />
        <el-form label-width="110px" style="max-width: 640px">
          <el-form-item label="条码规则" required>
            <WmsSelect v-model="generateForm.ruleCode" block placeholder="选择规则">
              <el-option
                v-for="r in tableData.filter((x) => x.status === 1)"
                :key="r.ruleCode"
                :label="`${r.ruleName} (${r.ruleCode})`"
                :value="r.ruleCode"
              />
            </WmsSelect>
          </el-form-item>
          <template v-if="generateForm.ruleCode">
            <el-form-item v-if="generateNeedsMaterial" label="物料编码">
              <el-input v-model="generateForm.materialCode" placeholder="从金蝶选取或手工输入">
                <template #append>
                  <el-button @click="openKingdeeMaterials('generate')">金蝶物料</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item v-if="generateNeedsBatch" label="批号">
              <el-input v-model="generateForm.batchNo" placeholder="从金蝶选取或手工输入">
                <template #append>
                  <el-button @click="openKingdeeBatches">金蝶批号</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item v-if="generateNeedsPackBarcode" label="包装条码">
              <el-input v-model="generateForm.packBarcode" placeholder="手工输入或从物料带入" />
            </el-form-item>
            <el-form-item v-if="generateNeedsSerial" label="序列号">
              <el-input v-model="generateForm.serialNo" :disabled="generateForm.autoSerial" placeholder="留空则自动分配">
                <template #append>
                  <el-button :disabled="generateForm.autoSerial" @click="openKingdeeSerials">金蝶序列号</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item v-if="generateNeedsSerial" label="自动序列号">
              <el-switch v-model="generateForm.autoSerial" />
              <span class="hint">开启后系统自动分配全局唯一序列号</span>
            </el-form-item>
          </template>
          <el-form-item label="标签尺寸(mm)">
            <el-input-number v-model="generateForm.labelWidthMm" :min="30" :max="300" :step="5" />
            <span style="margin: 0 8px">×</span>
            <el-input-number v-model="generateForm.labelHeightMm" :min="20" :max="200" :step="5" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="generateLoading" @click="handleGenerate">生成条码</el-button>
          </el-form-item>
        </el-form>

        <el-descriptions v-if="generateResult" title="生成结果" :column="2" border size="small" class="mt-16">
          <el-descriptions-item label="条码内容" :span="2">
            <strong>{{ generateResult.barcodeContent }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="规则">{{ generateResult.ruleCode }} v{{ generateResult.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ generateResult.barcodeType }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ generateResult.materialCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="批号">{{ generateResult.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="序列号">{{ generateResult.serialNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实例ID">{{ generateResult.instanceId }}</el-descriptions-item>
          <el-descriptions-item label="存档编号">{{ generateResult.archiveNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作">
            <el-button v-if="generateResult.archiveNo" type="primary" link @click="printGenerateResult">打印标签</el-button>
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- 条码存档 -->
      <el-tab-pane label="条码存档" name="archive">
        <el-alert
          title="生成或更新条码时自动存档；条码损坏时可从存档查询并重新打印，补打时保持原条码内容与打印参数不变。"
          type="info"
          :closable="false"
          show-icon
          class="mb-16"
        />
        <el-form :inline="true" :model="archiveQuery" class="mb-12">
          <el-form-item label="存档编号">
            <el-input v-model="archiveQuery.archiveNo" clearable placeholder="BA..." />
          </el-form-item>
          <el-form-item label="条码内容">
            <el-input v-model="archiveQuery.barcodeContent" clearable />
          </el-form-item>
          <el-form-item label="物料编码">
            <el-input v-model="archiveQuery.materialCode" clearable />
          </el-form-item>
          <el-form-item label="生成日期">
            <el-date-picker
              v-model="archiveDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadArchives">查询</el-button>
            <el-button @click="handleArchiveCleanup">清理过期存档</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="archiveLoading" :data="archiveData" stripe>
          <el-table-column prop="archiveNo" label="存档编号" width="150" />
          <el-table-column prop="barcodeContent" label="条码内容" min-width="180" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="物料" width="110" />
          <el-table-column prop="batchNo" label="批号" width="100" />
          <el-table-column prop="serialNo" label="序列号" width="110" />
          <el-table-column prop="barcodeType" label="编码类型" width="90" />
          <el-table-column label="标签尺寸" width="100">
            <template #default="{ row }">{{ row.labelWidthMm }}×{{ row.labelHeightMm }}mm</template>
          </el-table-column>
          <el-table-column prop="actionType" label="来源" width="80">
            <template #default="{ row }">
              {{ row.actionType === 'UPDATE' ? '更新' : row.actionType === 'REPRINT' ? '补打' : '生成' }}
            </template>
          </el-table-column>
          <el-table-column prop="reprintCount" label="补打次数" width="90" align="center" />
          <el-table-column prop="createTime" label="生成时间" width="120">
            <template #default="{ row }">
              <WmsDateText :value="row.createTime" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleArchiveReprint(row)">补打</el-button>
              <el-button link type="primary" @click="handleArchiveDirectPrint(row)">打印</el-button>
              <el-button link type="primary" @click="handleArchivePrint(row)">预览</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="archiveQuery.current"
          v-model:page-size="archiveQuery.size"
          :total="archiveTotal"
          layout="total, prev, pager, next"
          style="margin-top: 16px"
          @current-change="loadArchives"
        />
      </el-tab-pane>

      <!-- 追溯查询 -->
      <el-tab-pane label="追溯查询" name="trace">
        <el-form :inline="true" class="mb-12">
          <el-form-item label="条码">
            <el-input v-model="traceInput" placeholder="扫描或输入条码" style="width: 360px" @keyup.enter="handleTrace" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="traceLoading" @click="handleTrace">追溯</el-button>
          </el-form-item>
        </el-form>

        <template v-if="traceResult">
          <el-descriptions title="条码主档" :column="3" border size="small" class="mb-16">
            <el-descriptions-item label="条码">{{ traceResult.barcodeContent }}</el-descriptions-item>
            <el-descriptions-item label="物料">{{ traceResult.materialCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="批号">{{ traceResult.batchNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="序列号">{{ traceResult.serialNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="包装条码">{{ traceResult.packBarcode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="规则">{{ traceResult.ruleCode || '-' }} v{{ traceResult.versionNo || '-' }}</el-descriptions-item>
          </el-descriptions>

          <h4>流转记录</h4>
          <el-table :data="traceResult.links" stripe empty-text="暂无关联流转记录">
            <el-table-column prop="refType" label="业务类型" width="100" />
            <el-table-column prop="refNo" label="单据号" width="160" />
            <el-table-column prop="transactionNo" label="流水号" width="150" />
            <el-table-column prop="materialCode" label="物料" width="110" />
            <el-table-column prop="batchNo" label="批号" width="110" />
            <el-table-column prop="serialNo" label="序列号" width="120" />
            <el-table-column prop="remark" label="备注" min-width="120" />
            <el-table-column prop="createTime" label="时间" width="120">
              <template #default="{ row }">
                <WmsDateText :value="row.createTime" />
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>
    </el-tabs>
  </el-card>

  <!-- 规则编辑对话框（金蝶式分段配置） -->
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="860px" destroy-on-close>
    <el-row :gutter="16">
      <el-col :span="14">
        <el-form :model="form" label-width="100px">
          <el-form-item label="规则编码">
            <el-input v-model="form.ruleCode" :disabled="!!editingCode" placeholder="留空则系统自动生成" />
          </el-form-item>
          <el-form-item label="规则名称" required>
            <el-input v-model="form.ruleName" />
          </el-form-item>
          <el-form-item label="拼接模板">
            <WmsSelect v-model="form.templateCode" block clearable placeholder="请选择模板（可选）">
              <el-option v-for="t in TEMPLATE_OPTIONS" :key="t.code" :label="t.label" :value="t.code" />
            </WmsSelect>
          </el-form-item>
          <el-form-item label="默认分隔符">
            <el-input v-model="form.separator" style="width: 120px" placeholder="如 -" />
          </el-form-item>
          <el-form-item label="码制类型">
            <WmsSelect v-model="form.barcodeType">
              <el-option label="二维码 (QR)" value="QR" />
              <el-option label="CODE128" value="CODE128" />
              <el-option label="EAN13" value="EAN13" />
            </WmsSelect>
          </el-form-item>
          <el-form-item label="适用对象">
            <WmsSelect v-model="form.appliesTo">
              <el-option label="物料" value="MATERIAL" />
              <el-option label="批次" value="BATCH" />
              <el-option label="序列号" value="SERIAL" />
            </WmsSelect>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">禁用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="editingCode" label="变更说明">
            <el-input v-model="changeLog" placeholder="版本升级说明（可选）" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="form.description" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
      </el-col>
      <el-col :span="10">
        <div class="segment-panel">
          <div class="segment-panel-header">
            <span>分段配置</span>
            <el-button-group size="small">
              <el-button :icon="Plus" @click="addSegment('FIELD')">字段</el-button>
              <el-button @click="addSegment('SEPARATOR')">分隔符</el-button>
              <el-button @click="addSegment('FIXED')">固定值</el-button>
              <el-button @click="addSegment('DATE')">日期</el-button>
            </el-button-group>
          </div>
          <div v-if="!segments.length" class="segment-empty">选择模板或手动添加分段</div>
          <div v-for="(seg, idx) in segments" :key="idx" class="segment-row">
            <span class="segment-idx">{{ idx + 1 }}</span>
            <template v-if="seg.type === 'FIELD'">
              <WmsSelect v-model="seg.source" style="flex: 1" @change="syncSegmentsJson">
                <el-option v-for="o in SEGMENT_FIELD_OPTIONS" :key="o.source" :label="o.label" :value="o.source" />
              </WmsSelect>
            </template>
            <template v-else-if="seg.type === 'SEPARATOR'">
              <el-input v-model="seg.value" placeholder="分隔符" style="flex: 1" @input="syncSegmentsJson" />
            </template>
            <template v-else-if="seg.type === 'FIXED'">
              <el-input v-model="seg.value" placeholder="固定文本" style="flex: 1" @input="syncSegmentsJson" />
            </template>
            <template v-else>
              <el-tag type="info">日期(yyyyMMdd)</el-tag>
            </template>
            <el-button-group size="small">
              <el-button :icon="ArrowUp" :disabled="idx === 0" @click="moveSegment(idx, -1)" />
              <el-button :icon="ArrowDown" :disabled="idx === segments.length - 1" @click="moveSegment(idx, 1)" />
              <el-button :icon="Delete" type="danger" @click="removeSegment(idx)" />
            </el-button-group>
          </div>
          <div class="preview-box">
            <div class="preview-label">预览示例</div>
            <code>{{ assemblePreview(segments, form.separator || '', {
              MATERIAL_CODE: 'MAT001', BATCH_NO: 'B20260101', PACK_BARCODE: 'PKG001', SERIAL_NO: 'SN00000001'
            }) }}</code>
          </div>
        </div>
      </el-col>
    </el-row>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>

  <!-- 版本历史 -->
  <el-drawer v-model="versionDrawerVisible" :title="`版本历史 - ${versionRuleCode}`" size="480px">
    <el-timeline>
      <el-timeline-item
        v-for="v in versionList"
        :key="v.id"
        :timestamp="formatDate(v.createTime)"
        placement="top"
      >
        <p><strong>v{{ v.versionNo }}</strong> — {{ v.ruleName }}</p>
        <p class="text-muted">{{ v.changeLog || '无变更说明' }}</p>
        <el-tag size="small">{{ TEMPLATE_OPTIONS.find((t) => t.code === v.templateCode)?.label || '自定义' }}</el-tag>
        <WmsStatusTag :status="v.status" />
      </el-timeline-item>
    </el-timeline>
  </el-drawer>

  <!-- 解析测试 -->
  <el-dialog v-model="parseVisible" title="条码解析测试" width="520px">
    <el-form label-width="90px">
      <el-form-item label="规则">
        <WmsSelect v-model="parseRuleCode" block>
          <el-option v-for="r in tableData" :key="r.ruleCode" :label="r.ruleName" :value="r.ruleCode" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="条码值">
        <el-input v-model="parseInput" @keyup.enter="handleParse">
          <template #append>
            <el-button :loading="parseLoading" @click="handleParse">解析</el-button>
          </template>
        </el-input>
      </el-form-item>
    </el-form>
    <el-descriptions v-if="parseResult" :column="1" border size="small">
      <el-descriptions-item label="物料编码">{{ parseResult.materialCode || parseResult.segments?.MATERIAL_CODE || '-' }}</el-descriptions-item>
      <el-descriptions-item label="批号">{{ parseResult.batchNo || parseResult.segments?.BATCH_NO || '-' }}</el-descriptions-item>
      <el-descriptions-item label="序列号">{{ parseResult.serialNo || parseResult.segments?.SERIAL_NO || '-' }}</el-descriptions-item>
      <el-descriptions-item label="包装条码">{{ parseResult.packBarcode || parseResult.segments?.PACK_BARCODE || '-' }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>

  <!-- 金蝶物料选择 -->
  <el-dialog v-model="kingdeeMaterialVisible" title="金蝶云星空 - 物料" width="640px">
    <el-input v-model="kingdeeMaterialKeyword" placeholder="编码/名称" class="mb-12" @keyup.enter="loadKingdeeMaterials">
      <template #append><el-button @click="loadKingdeeMaterials">查询</el-button></template>
    </el-input>
    <el-table v-loading="kingdeeMaterialLoading" :data="kingdeeMaterials" stripe max-height="360" @row-click="pickMaterial">
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="名称" min-width="140" />
      <el-table-column prop="barCode" label="物料条码" width="130" />
      <el-table-column prop="packBarCode" label="包装条码" width="120" />
      <el-table-column label="批次/序列" width="90">
        <template #default="{ row }">
          {{ row.batchManaged ? '批' : '' }}{{ row.serialManaged ? '序' : '' }}
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>

  <!-- 金蝶批号选择 -->
  <el-dialog v-model="kingdeeBatchVisible" title="金蝶云星空 - 批号" width="520px">
    <el-table v-loading="kingdeeBatchLoading" :data="kingdeeBatches" stripe @row-click="pickBatch">
      <el-table-column prop="batchNo" label="批号" width="140" />
      <el-table-column prop="materialCode" label="物料" width="120" />
      <el-table-column prop="materialName" label="名称" />
    </el-table>
  </el-dialog>

  <!-- 金蝶序列号选择 -->
  <el-dialog v-model="kingdeeSerialVisible" title="金蝶云星空 - 序列号" width="520px">
    <el-table v-loading="kingdeeSerialLoading" :data="kingdeeSerials" stripe @row-click="pickSerial">
      <el-table-column prop="serialNo" label="序列号" width="160" />
      <el-table-column prop="materialCode" label="物料" width="120" />
      <el-table-column prop="batchNo" label="批号" />
    </el-table>
  </el-dialog>
</template>

<style scoped>
.mb-12 { margin-bottom: 12px; }
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
.ml-8 { margin-left: 8px; }
.hint { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.text-muted { color: var(--el-text-color-secondary); font-size: 13px; }
.segment-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
  background: var(--el-fill-color-blank);
}
.segment-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
}
.segment-empty { color: var(--el-text-color-placeholder); text-align: center; padding: 24px 0; }
.segment-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.segment-idx {
  width: 22px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.preview-box {
  margin-top: 12px;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
.preview-label { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.preview-box code { word-break: break-all; font-size: 13px; }
</style>
