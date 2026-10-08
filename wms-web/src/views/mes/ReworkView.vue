<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  closeMesRework,
  completeMesRework,
  createMesDefect,
  getMesDefects,
  getMesOutbox,
  getMesReportContext,
  retryMesOutbox,
  secondaryMesRework,
  type MesDefect,
  type MesOpPlan,
} from '@/api/mes'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('mes:rework:create'))
const canComplete = computed(() => userStore.hasPermission('mes:rework:complete'))
const canSecondary = computed(() => userStore.hasPermission('mes:rework:secondary'))
const canClose = computed(() => userStore.hasPermission('mes:rework:close'))

const loading = ref(false)
const tableData = ref<MesDefect[]>([])
const total = ref(0)
const query = reactive({ moNo: '', reworkStatus: '', current: 1, size: 20 })

const form = reactive({
  moNo: '',
  sourceProcessCode: '',
  defectQty: 1,
  defectType: 'APPEARANCE',
  defectDesc: '',
  ownerName: '',
  reworkProcessCodes: [] as string[],
})
const plans = ref<MesOpPlan[]>([])

async function loadData() {
  loading.value = true
  try {
    const res = await getMesDefects(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadPlans() {
  if (!form.moNo.trim()) return
  const ctx = await getMesReportContext(form.moNo.trim())
  plans.value = ctx.plans || []
  if (plans.value.length && !form.sourceProcessCode) {
    form.sourceProcessCode = plans.value[0].processCode || ''
  }
}

async function handleCreate() {
  const vo = await createMesDefect({ ...form, moNo: form.moNo.trim() })
  ElMessage.success(`已创建返工 ${vo.defect?.defectNo}`)
  loadData()
}

async function confirmAction(row: MesDefect, text: string, fn: (no: string) => Promise<unknown>) {
  await ElMessageBox.confirm(`确定对不良单 ${row.defectNo} ${text}？`, '提示')
  await fn(row.defectNo!)
  ElMessage.success('操作成功')
  loadData()
}

async function retryDefectSync(row: MesDefect) {
  const res = await getMesOutbox({ bizNo: row.defectNo, size: 50 })
  const targets = (res.records || []).filter(
    (t) => t.syncStatus === 'FAILED' || t.syncStatus === 'MANUAL_REQUIRED',
  )
  if (!targets.length) {
    ElMessage.info('无可重试的同步任务')
    return
  }
  for (const t of targets) {
    await retryMesOutbox(t.id!)
  }
  ElMessage.success('已加入重试队列')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-row :gutter="16">
    <el-col v-if="canCreate" :span="10">
      <el-card shadow="never">
        <template #header>发起返工</template>
        <el-form :model="form" label-width="100px">
          <el-form-item label="源工单号" required>
            <el-input v-model="form.moNo" @blur="loadPlans" />
          </el-form-item>
          <el-form-item label="源工序" required>
            <WmsSelect v-model="form.sourceProcessCode" filterable>
              <el-option
                v-for="p in plans"
                :key="p.processCode"
                :label="`${p.processName} 已报${p.reportedQty}`"
                :value="p.processCode"
              />
            </WmsSelect>
          </el-form-item>
          <el-form-item label="不良数量" required>
            <el-input-number v-model="form.defectQty" :min="0.0001" :precision="4" />
          </el-form-item>
          <el-form-item label="不良类型" required>
            <WmsSelect v-model="form.defectType">
              <el-option label="外观" value="APPEARANCE" />
              <el-option label="尺寸" value="SIZE" />
              <el-option label="功能" value="FUNCTION" />
              <el-option label="其他" value="OTHER" />
            </WmsSelect>
          </el-form-item>
          <el-form-item label="返工工序">
            <WmsSelect v-model="form.reworkProcessCodes" multiple filterable placeholder="空则按工艺路线自动生成">
              <el-option
                v-for="p in plans"
                :key="'rw-' + p.processCode"
                :label="`${p.processName} (${p.processCode})`"
                :value="p.processCode"
              />
            </WmsSelect>
          </el-form-item>
          <el-form-item label="不良描述">
            <el-input v-model="form.defectDesc" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="责任人">
            <el-input v-model="form.ownerName" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleCreate">发起返工</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>
    <el-col :span="canCreate ? 14 : 24">
      <el-card shadow="never">
        <template #header>不良 / 返工单</template>
        <el-form :inline="true">
          <el-form-item label="工单号">
            <el-input v-model="query.moNo" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <WmsSelect v-model="query.reworkStatus" clearable placeholder="全部">
              <el-option label="返工中" value="REWORKING" />
              <el-option label="返工完成" value="DONE" />
              <el-option label="二次返工中" value="SECONDARY" />
              <el-option label="已关闭" value="CLOSED" />
            </WmsSelect>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadData">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="tableData" stripe>
          <el-table-column label="不良单号" width="150">
            <template #default="{ row }">
              <RouterLink class="detail-link" :to="`/mes/rework/${encodeURIComponent(row.defectNo)}`">
                {{ row.defectNo }}
              </RouterLink>
            </template>
          </el-table-column>
          <el-table-column prop="moNo" label="工单" width="130" />
          <el-table-column prop="sourceProcessName" label="源工序" width="110" />
          <el-table-column prop="defectQty" label="不良数" width="80" />
          <el-table-column prop="defectType" label="类型" width="90" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }"><OrderStatusTag :status="row.reworkStatus" /></template>
          </el-table-column>
          <el-table-column label="不良单同步" width="110">
            <template #default="{ row }">
              <OrderStatusTag :status="row.defectSyncStatus" pending-label="待同步" failed-label="同步失败" />
            </template>
          </el-table-column>
          <el-table-column label="返工单同步" width="110">
            <template #default="{ row }">
              <OrderStatusTag :status="row.reworkSyncStatus" pending-label="待同步" failed-label="同步失败" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="
                  (row.defectSyncStatus === 'FAILED' || row.defectSyncStatus === 'MANUAL_REQUIRED'
                    || row.reworkSyncStatus === 'FAILED' || row.reworkSyncStatus === 'MANUAL_REQUIRED')
                  && userStore.hasPermission('mes:sync:panel')
                "
                link
                type="warning"
                @click="retryDefectSync(row)"
              >
                同步重试
              </el-button>
              <el-button
                v-if="row.reworkStatus === 'REWORKING' && canComplete"
                link
                type="success"
                @click="confirmAction(row, '完成返工', completeMesRework)"
              >
                完成返工
              </el-button>
              <el-button
                v-if="(row.reworkStatus === 'REWORKING' || row.reworkStatus === 'DONE') && canSecondary"
                link
                type="warning"
                @click="confirmAction(row, '标记二次返工', secondaryMesRework)"
              >
                再次不良
              </el-button>
              <el-button
                v-if="(row.reworkStatus === 'DONE' || row.reworkStatus === 'SECONDARY') && canClose"
                link
                type="info"
                @click="confirmAction(row, '关闭返工', closeMesRework)"
              >
                关闭
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="query.current"
          :total="total"
          layout="total, prev, pager, next"
          style="margin-top: 16px"
          @current-change="loadData"
        />
      </el-card>
    </el-col>
  </el-row>
</template>

<style scoped>
.detail-link {
  color: var(--el-color-primary);
}
</style>
