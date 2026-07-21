<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLocationTree, createLocation, createZone, type LocationNode } from '@/api/location'
import { getWarehouses, type Warehouse } from '@/api/warehouse'
import { getStatusMeta } from '@/utils/status'

const route = useRoute()

const loading = ref(false)
const treeData = ref<LocationNode[]>([])
const treeKey = ref(0)
const warehouseFilter = ref('')
const warehouseOptions = ref<Warehouse[]>([])
const selectedNode = ref<LocationNode | null>(null)
const dialogVisible = ref(false)

type CreateMode = 'zone' | 'location'

const createMode = computed<CreateMode | null>(() => {
  if (!selectedNode.value) return null
  if (selectedNode.value.type === 'warehouse') return 'zone'
  if (selectedNode.value.type === 'zone') return 'location'
  return null
})

const addButtonText = computed(() => {
  if (createMode.value === 'zone') return '新增区域'
  if (createMode.value === 'location') return '新增库位'
  return '新增'
})

const dialogTitle = computed(() => (createMode.value === 'zone' ? '新增区域' : '新增库位'))

const parentSummary = computed(() => {
  if (!selectedNode.value || !createMode.value) return ''
  if (createMode.value === 'zone') {
    return displayNodeLabel(selectedNode.value)
  }
  const wh = selectedNode.value.warehouseCode ?? ''
  const zone = selectedNode.value.zoneCode ?? selectedNode.value.id
  const whOpt = warehouseOptions.value.find((w) => w.warehouseCode === wh)
  const whName = whOpt?.warehouseName ?? wh
  const zoneName = selectedNode.value.zoneName ?? zone
  return `${whName} (${wh}) / ${zoneName} (${zone})`
})

const zoneForm = ref({ zoneCode: '', zoneName: '' })
const locationForm = ref({ locationCode: '', locationName: '', locationType: 'STORAGE' })

async function loadWarehouses() {
  const res = await getWarehouses({ current: 1, size: 200 })
  warehouseOptions.value = res.records
  if (warehouseFilter.value && !warehouseOptions.value.some((w) => w.warehouseCode === warehouseFilter.value)) {
    warehouseFilter.value = ''
  }
}

async function loadTree() {
  loading.value = true
  try {
    treeData.value = await getLocationTree(warehouseFilter.value || undefined)
    treeKey.value++
  } finally {
    loading.value = false
  }
}

function onNodeClick(data: LocationNode) {
  selectedNode.value = data
}

function openCreateDialog() {
  if (!selectedNode.value || !createMode.value) {
    ElMessage.warning('请先选中仓库（可新增区域）或区域（可新增库位）')
    return
  }
  if (createMode.value === 'zone') {
    zoneForm.value = { zoneCode: '', zoneName: '' }
  } else {
    locationForm.value = { locationCode: '', locationName: '', locationType: 'STORAGE' }
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (createMode.value === 'zone') {
    await submitZone()
  } else if (createMode.value === 'location') {
    await submitLocation()
  }
}

async function submitZone() {
  const node = selectedNode.value
  if (!node || node.type !== 'warehouse') return
  const warehouseCode = node.warehouseCode ?? node.id.replace(/^WH-/, '')
  const zoneCode = zoneForm.value.zoneCode?.trim()
  if (!zoneCode) {
    ElMessage.warning('请填写区域编码')
    return
  }
  try {
    await createZone({
      warehouseCode,
      zoneCode,
      zoneName: zoneForm.value.zoneName?.trim() || zoneCode,
    })
    ElMessage.success('区域创建成功')
    dialogVisible.value = false
    warehouseFilter.value = warehouseCode
    await loadTree()
  } catch {
    // 错误已由 request 拦截器提示
  }
}

async function submitLocation() {
  const node = selectedNode.value
  if (!node || node.type !== 'zone') return
  const locationCode = locationForm.value.locationCode?.trim()
  if (!locationCode) {
    ElMessage.warning('请填写库位编码')
    return
  }
  try {
    await createLocation({
      locationCode,
      locationName: locationForm.value.locationName?.trim() || locationCode,
      warehouseCode: node.warehouseCode!,
      zoneCode: node.zoneCode ?? node.id,
      locationType: locationForm.value.locationType,
      status: 1,
    })
    ElMessage.success('库位创建成功')
    dialogVisible.value = false
    warehouseFilter.value = node.warehouseCode ?? ''
    await loadTree()
  } catch {
    // 错误已由 request 拦截器提示
  }
}

function statusTag(status?: string) {
  const meta = getStatusMeta(status || 'FREE')
  return { type: meta.type, text: meta.label }
}

function displayNodeLabel(data: LocationNode) {
  if (data.label && data.label.includes('【')) {
    return data.label
  }
  if (data.type === 'warehouse') {
    const code = data.warehouseCode ?? data.id.replace(/^WH-/, '')
    const wh = warehouseOptions.value.find((w) => w.warehouseCode === code)
    const name = data.warehouseName ?? wh?.warehouseName ?? code
    return `【仓库】${name} (${code})`
  }
  if (data.type === 'zone') {
    const name = data.zoneName ?? data.zoneCode ?? data.id
    return `【区域】${name} (${data.zoneCode ?? data.id})`
  }
  return data.label || data.id
}

const selectionHint = computed(() => {
  if (!selectedNode.value) return '未选中节点'
  if (selectedNode.value.type === 'warehouse') return '当前为仓库，可新增区域'
  if (selectedNode.value.type === 'zone') return '当前为区域，可新增库位'
  return '当前为库位，请选择上级仓库或区域'
})

async function refreshPageData() {
  await loadWarehouses()
  await loadTree()
}

onMounted(refreshPageData)

watch(
  () => route.fullPath,
  () => {
    if (route.path.includes('/base/locations')) {
      refreshPageData()
    }
  },
)
</script>

<template>
  <el-row :gutter="16">
    <el-col :span="10">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>库位树</span>
            <el-button
              type="primary"
              size="small"
              :disabled="!createMode"
              @click="openCreateDialog"
            >
              {{ addButtonText }}
            </el-button>
          </div>
        </template>
        <WmsSelect
          v-model="warehouseFilter"
          placeholder="筛选仓库"
          clearable
          filterable
          block
          class="mb-12 filter-select"
          @change="loadTree"
        >
          <el-option
            v-for="wh in warehouseOptions"
            :key="wh.warehouseCode"
            :label="`${wh.warehouseName} (${wh.warehouseCode})`"
            :value="wh.warehouseCode"
          />
        </WmsSelect>
        <p class="selected-tip">{{ selectionHint }}</p>
        <el-tree
          :key="treeKey"
          v-loading="loading"
          :data="treeData"
          node-key="id"
          highlight-current
          default-expand-all
          :props="{ label: 'label', children: 'children' }"
          @node-click="onNodeClick"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <el-tag size="small" effect="plain" class="type-tag">
                {{ data.type === 'warehouse' ? '仓' : data.type === 'zone' ? '区' : '位' }}
              </el-tag>
              {{ displayNodeLabel(data) }}
              <el-tag v-if="data.type === 'location'" size="small" :type="statusTag(data.occupyStatus).type">
                {{ statusTag(data.occupyStatus).text }}
              </el-tag>
            </span>
          </template>
        </el-tree>
      </el-card>
    </el-col>
    <el-col :span="14">
      <el-card shadow="never">
        <template #header>新增规则</template>
        <ul class="help-list">
          <li><strong>仓库</strong>下只能新增<strong>区域</strong></li>
          <li><strong>区域</strong>下只能新增<strong>库位</strong></li>
          <li>库位节点下不可再新增，请选中上级节点</li>
          <li>选中节点后点击右上角按钮即可新增</li>
        </ul>
      </el-card>
    </el-col>
  </el-row>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
    <p class="parent-hint">上级：{{ parentSummary }}</p>

    <el-form v-if="createMode === 'zone'" label-width="90px">
      <el-form-item label="区域编码" required>
        <el-input v-model="zoneForm.zoneCode" placeholder="如 A1、B2" />
      </el-form-item>
      <el-form-item label="区域名称">
        <el-input v-model="zoneForm.zoneName" placeholder="默认同区域编码" />
      </el-form-item>
    </el-form>

    <el-form v-else-if="createMode === 'location'" label-width="90px">
      <el-form-item label="库位编码" required>
        <el-input v-model="locationForm.locationCode" placeholder="如 WH01-A1-001" />
      </el-form-item>
      <el-form-item label="库位名称">
        <el-input v-model="locationForm.locationName" placeholder="默认同库位编码" />
      </el-form-item>
      <el-form-item label="库位类型">
        <WmsSelect v-model="locationForm.locationType" block>
          <el-option label="存储位 STORAGE" value="STORAGE" />
          <el-option label="原料位 RAW" value="RAW" />
          <el-option label="拣选位 PICK" value="PICK" />
        </WmsSelect>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.mb-12 { margin-bottom: 12px; }
.filter-select { width: 100%; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; }
.type-tag { min-width: 22px; text-align: center; }
.selected-tip { font-size: 13px; color: var(--el-text-color-secondary); margin: 0 0 8px; }
.parent-hint { margin: 0 0 16px; color: var(--el-color-primary); font-size: 13px; }
.help-list { margin: 0; padding-left: 20px; line-height: 2; color: var(--el-text-color-regular); }
</style>
