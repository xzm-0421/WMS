<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getOutboundOrders,
  getOutboundOrder,
  createOutboundOrder,
  submitOutboundOrder,
  auditOutboundOrder,
  reverseAuditOutboundOrder,
  cancelOutboundOrder,
  recommendLocations,
  type OutboundOrder,
  type OutboundOrderDetail,
  type RecommendLocation,
} from '@/api/outbound'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'
import PrintActions from '@/components/PrintActions.vue'
import MaterialSelectInput from '@/components/MaterialSelectInput.vue'
import { applyMaterialBasic, type Material } from '@/api/material'
import { getDictLabel } from '@/utils/dict'

const loading = ref(false)
const tableData = ref<OutboundOrder[]>([])
const total = ref(0)
const query = reactive({ orderNo: '', warehouseCode: '', status: '', current: 1, size: 20 })

const dialogVisible = ref(false)
const form = reactive<OutboundOrder>({
  orderType: 'SALES',
  warehouseCode: 'WH01',
  planDate: new Date().toISOString().slice(0, 10),
  remark: '',
})

const detailLines = ref<OutboundOrderDetail[]>([])
const drawerVisible = ref(false)
const currentOrder = ref<OutboundOrder | null>(null)
const detailLoading = ref(false)
const recommendVisible = ref(false)
const recommendList = ref<RecommendLocation[]>([])
const recommendLoading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await getOutboundOrders(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  Object.assign(form, {
    orderType: 'SALES',
    warehouseCode: 'WH01',
    planDate: new Date().toISOString().slice(0, 10),
    remark: '',
  })
  detailLines.value = [{ materialCode: '', materialName: '', unitCode: 'KG', demandQty: 1 }]
  dialogVisible.value = true
}

function addLine() {
  detailLines.value.push({ materialCode: '', materialName: '', unitCode: 'KG', demandQty: 1 })
}

function removeLine(index: number) {
  detailLines.value.splice(index, 1)
}

function onMaterialSelect(row: OutboundOrderDetail, material: Material) {
  applyMaterialBasic(row as unknown as Record<string, unknown>, material)
}

async function handleCreate() {
  await createOutboundOrder({ ...form, details: detailLines.value })
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadData()
}

async function showDetail(row: OutboundOrder) {
  detailLoading.value = true
  drawerVisible.value = true
  try {
    currentOrder.value = await getOutboundOrder(row.orderNo!)
  } finally {
    detailLoading.value = false
  }
}

async function handleRecommend(orderNo: string) {
  recommendLoading.value = true
  recommendVisible.value = true
  try {
    recommendList.value = await recommendLocations(orderNo)
  } finally {
    recommendLoading.value = false
  }
}

async function handleAction(action: 'submit' | 'audit' | 'unaudit' | 'cancel', orderNo: string) {
  const labels = { submit: '提交', audit: '审核', unaudit: '反审核', cancel: '取消' }
  await ElMessageBox.confirm(`确定${labels[action]}出库单 ${orderNo}？`, '提示')
  if (action === 'submit') await submitOutboundOrder(orderNo)
  else if (action === 'audit') await auditOutboundOrder(orderNo)
  else if (action === 'unaudit') await reverseAuditOutboundOrder(orderNo)
  else await cancelOutboundOrder(orderNo)
  ElMessage.success(`${labels[action]}成功`)
  if (currentOrder.value?.orderNo === orderNo) {
    currentOrder.value = await getOutboundOrder(orderNo)
  }
  loadData()
}

const canReverseAudit = computed(() => {
  const order = currentOrder.value
  if (!order || order.status !== 'PICKING') return false
  return !(order.details ?? []).some((d) => (d.issuedQty ?? 0) > 0)
})

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="单号">
        <el-input v-model="query.orderNo" clearable />
      </el-form-item>
      <el-form-item label="仓库">
        <el-input v-model="query.warehouseCode" clearable placeholder="WH01" />
      </el-form-item>
      <el-form-item label="状态">
        <WmsSelect v-model="query.status" clearable placeholder="全部">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="出库中" value="OUTBOUND" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="handleAdd">新建出库单</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="orderNo" label="出库单号" width="160" />
      <el-table-column prop="orderType" label="类型" width="100">
        <template #default="{ row }">
          {{ getDictLabel('outboundType', row.orderType) }}
        </template>
      </el-table-column>
      <el-table-column prop="warehouseCode" label="仓库" width="100" />
      <el-table-column prop="planDate" label="计划日期" width="120">
        <template #default="{ row }">
          <WmsDateText :value="row.planDate" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <OrderStatusTag :status="row.status || 'DRAFT'" />
        </template>
      </el-table-column>
      <el-table-column prop="creatorName" label="创建人" width="100" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
          <el-button
            v-if="row.status !== 'DRAFT' && row.status !== 'CANCELLED'"
            link
            type="warning"
            @click="handleRecommend(row.orderNo!)"
          >
            推荐库位
          </el-button>
          <PrintActions biz="outbound_order" :doc-no="row.orderNo" />
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

  <el-dialog v-model="dialogVisible" title="新建出库单" width="720px">
    <el-form :model="form" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="出库类型" required>
            <WmsSelect v-model="form.orderType">
              <el-option label="销售出库" value="SALES" />
              <el-option label="生产领料" value="PRODUCTION" />
              <el-option label="调拨出库" value="TRANSFER" />
            </WmsSelect>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="仓库" required>
            <el-input v-model="form.warehouseCode" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划日期" required>
            <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="备注">
        <el-input v-model="form.remark" />
      </el-form-item>
    </el-form>
    <div class="detail-header">
      <span>明细行</span>
      <el-button size="small" @click="addLine">添加行</el-button>
    </div>
    <el-table :data="detailLines" size="small" border>
      <el-table-column label="物料编码" width="180">
        <template #default="{ row }">
          <MaterialSelectInput
            v-model="row.materialCode"
            size="small"
            @select="onMaterialSelect(row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column label="物料名称" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.materialName" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="单位" width="80">
        <template #default="{ row }">
          <el-input v-model="row.unitCode" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="需求数量" width="100">
        <template #default="{ row }">
          <el-input-number v-model="row.demandQty" :min="0" :precision="4" :step="0.001" size="small" controls-position="right" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ $index }">
          <el-button link type="danger" size="small" @click="removeLine($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleCreate">确定</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="drawerVisible" title="出库单详情" size="640px">
    <div v-loading="detailLoading">
      <template v-if="currentOrder">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="单号">{{ currentOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <OrderStatusTag :status="currentOrder.status || 'DRAFT'" />
          </el-descriptions-item>
          <el-descriptions-item label="出库类型">{{ getDictLabel('outboundType', currentOrder.orderType) }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ currentOrder.warehouseCode }}</el-descriptions-item>
          <el-descriptions-item label="计划日期"><WmsDateText :value="currentOrder.planDate" /></el-descriptions-item>
          <el-descriptions-item label="客户">{{ currentOrder.customerCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ currentOrder.creatorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ currentOrder.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <p class="detail-section-title">明细行</p>
        <el-table :data="currentOrder.details || []" stripe size="small">
          <el-table-column prop="lineNo" label="行号" width="60" />
          <el-table-column prop="materialCode" label="物料编码" width="120" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" />
          <el-table-column prop="demandQty" label="需求数量" width="90" />
          <el-table-column prop="issuedQty" label="已发数量" width="90" />
          <el-table-column prop="sourceLocation" label="源库位" width="120" />
        </el-table>

        <div class="drawer-actions">
          <el-button type="warning" @click="handleRecommend(currentOrder.orderNo!)">FIFO 推荐库位</el-button>
          <el-button
            v-if="currentOrder.status === 'DRAFT'"
            type="primary"
            @click="handleAction('submit', currentOrder.orderNo!)"
          >
            提交
          </el-button>
          <el-button
            v-if="currentOrder.status === 'PENDING'"
            type="success"
            @click="handleAction('audit', currentOrder.orderNo!)"
          >
            审核
          </el-button>
          <el-button
            v-if="canReverseAudit"
            type="warning"
            @click="handleAction('unaudit', currentOrder.orderNo!)"
          >
            反审核
          </el-button>
          <el-button
            v-if="currentOrder.status !== 'COMPLETED' && currentOrder.status !== 'CANCELLED'"
            type="danger"
            @click="handleAction('cancel', currentOrder.orderNo!)"
          >
            取消
          </el-button>
        </div>
      </template>
    </div>
  </el-drawer>

  <el-dialog v-model="recommendVisible" title="FIFO 推荐库位" width="640px">
    <el-table v-loading="recommendLoading" :data="recommendList" stripe size="small">
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="batchNo" label="批次" width="120" />
      <el-table-column prop="locationCode" label="推荐库位" width="140" />
      <el-table-column prop="availableQty" label="可用数量" width="100" />
      <el-table-column prop="expireDate" label="到期日" width="120">
        <template #default="{ row }">
          <WmsDateText :value="row.expireDate" />
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0 8px;
  font-weight: 500;
}
.drawer-actions {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.detail-section-title {
  margin: 16px 0 8px;
  font-weight: 500;
  font-size: 14px;
}
</style>
