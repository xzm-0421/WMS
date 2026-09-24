<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getQcOrders,
  judgeQcOrder,
  concessionQcOrder,
  type QcOrder,
} from '@/api/quality'

const loading = ref(false)
const tableData = ref<QcOrder[]>([])
const total = ref(0)
const query = reactive({ orderNo: '', materialCode: '', status: '', current: 1, size: 20 })

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const statusMeta: Record<string, { label: string; type: TagType }> = {
  PENDING: { label: '待检', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  REJECTED: { label: '不合格', type: 'danger' },
  CONCESSION_PENDING: { label: '待让步', type: 'warning' },
  CONCESSION_ACCEPTED: { label: '已让步', type: 'info' },
}

function statusLabel(status?: string) {
  return (status && statusMeta[status]?.label) || status || '-'
}

function statusType(status?: string): TagType {
  return (status && statusMeta[status]?.type) || 'info'
}

const judgeVisible = ref(false)
const judgeForm = reactive({
  qcNo: '',
  qcQty: 0,
  qualifiedQty: 0,
  unqualifiedQty: 0,
  judgeResult: 'QUALIFIED',
  judgeRemark: '',
})

const concessionVisible = ref(false)
const concessionForm = reactive({ qcNo: '', concessionReason: '', approver: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await getQcOrders(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleJudge(row: QcOrder) {
  Object.assign(judgeForm, {
    qcNo: row.qcNo,
    qcQty: row.qcQty ?? 0,
    qualifiedQty: row.qcQty ?? 0,
    unqualifiedQty: 0,
    judgeResult: 'QUALIFIED',
    judgeRemark: '',
  })
  judgeVisible.value = true
}

async function submitJudge() {
  await judgeQcOrder(judgeForm.qcNo!, {
    qualifiedQty: judgeForm.qualifiedQty,
    unqualifiedQty: judgeForm.unqualifiedQty,
    judgeResult: judgeForm.judgeResult,
    judgeRemark: judgeForm.judgeRemark,
  })
  ElMessage.success('判定成功')
  judgeVisible.value = false
  loadData()
}

function handleConcession(row: QcOrder) {
  Object.assign(concessionForm, { qcNo: row.qcNo, concessionReason: '', approver: '' })
  concessionVisible.value = true
}

async function submitConcession() {
  await concessionQcOrder(concessionForm.qcNo!, {
    concessionReason: concessionForm.concessionReason,
    approver: concessionForm.approver,
  })
  ElMessage.success('让步接收成功')
  concessionVisible.value = false
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="质检单号">
        <el-input v-model="query.orderNo" clearable />
      </el-form-item>
      <el-form-item label="物料编码">
        <el-input v-model="query.materialCode" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <WmsSelect v-model="query.status" clearable placeholder="全部">
          <el-option label="待检" value="PENDING" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="不合格" value="REJECTED" />
          <el-option label="待让步" value="CONCESSION_PENDING" />
          <el-option label="已让步" value="CONCESSION_ACCEPTED" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="qcNo" label="质检单号" width="160" />
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="批次" width="110" />
      <el-table-column prop="qcQty" label="送检数量" width="100" />
      <el-table-column prop="qualifiedQty" label="合格数量" width="100" />
      <el-table-column prop="unqualifiedQty" label="不合格数量" width="110" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="judgeResult" label="判定" width="100" />
      <el-table-column prop="createTime" label="创建时间" width="170">
        <template #default="{ row }">
          <WmsDateText :value="row.createTime" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="primary" @click="handleJudge(row)">
            判定
          </el-button>
          <el-button
            v-if="row.status === 'CONCESSION_PENDING'"
            link
            type="warning"
            @click="handleConcession(row)"
          >
            让步接收
          </el-button>
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

  <el-dialog v-model="judgeVisible" title="质检判定" width="520px">
    <el-form :model="judgeForm" label-width="100px">
      <el-form-item label="合格数量">
        <el-input-number v-model="judgeForm.qualifiedQty" :min="0" :precision="4" :step="0.001" />
      </el-form-item>
      <el-form-item label="不合格数量">
        <el-input-number v-model="judgeForm.unqualifiedQty" :min="0" :precision="4" :step="0.001" />
      </el-form-item>
      <el-form-item label="判定结果" required>
        <WmsSelect v-model="judgeForm.judgeResult">
          <el-option label="合格" value="QUALIFIED" />
          <el-option label="不合格" value="UNQUALIFIED" />
          <el-option label="让步接收" value="CONCESSION" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="判定说明">
        <el-input v-model="judgeForm.judgeRemark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="judgeVisible = false">取消</el-button>
      <el-button type="primary" @click="submitJudge">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="concessionVisible" title="让步接收" width="520px">
    <el-form :model="concessionForm" label-width="100px">
      <el-form-item label="让步原因" required>
        <el-input v-model="concessionForm.concessionReason" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="审批人">
        <el-input v-model="concessionForm.approver" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="concessionVisible = false">取消</el-button>
      <el-button type="primary" @click="submitConcession">确定</el-button>
    </template>
  </el-dialog>
</template>
