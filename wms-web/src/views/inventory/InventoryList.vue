<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  getInventoryList,
  getInventoryTransactions,
  getInventoryWarnings,
  type InventoryItem,
  type InventoryTransaction,
  type InventoryWarning,
} from '@/api/inventory'

const activeTab = ref('list')
const loading = ref(false)

const listData = ref<InventoryItem[]>([])
const listTotal = ref(0)
const listQuery = reactive({ warehouseCode: '', materialCode: '', current: 1, size: 20 })

const txnData = ref<InventoryTransaction[]>([])
const txnTotal = ref(0)
const txnQuery = reactive({ warehouseCode: '', materialCode: '', transactionType: '', current: 1, size: 20 })

const warnData = ref<InventoryWarning[]>([])
const warnTotal = ref(0)
const warnQuery = reactive({ warehouseCode: '', current: 1, size: 20 })

async function loadList() {
  loading.value = true
  try {
    const res = await getInventoryList(listQuery)
    listData.value = res.records || []
    listTotal.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadTransactions() {
  loading.value = true
  try {
    const res = await getInventoryTransactions(txnQuery)
    txnData.value = res.records
    txnTotal.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadWarnings() {
  loading.value = true
  try {
    const res = await getInventoryWarnings(warnQuery)
    warnData.value = res.records
    warnTotal.value = res.total
  } finally {
    loading.value = false
  }
}

function handleTabChange(tab: string | number) {
  if (tab === 'list') loadList()
  else if (tab === 'txn') loadTransactions()
  else loadWarnings()
}

const TXN_TYPE_MAP: Record<string, string> = {
  INBOUND: '入库',
  OUTBOUND: '出库',
  TRANSFER: '移库',
  ADJUST: '调整',
  FREEZE: '冻结',
  UNFREEZE: '解冻',
}

const WARN_TYPE_MAP: Record<string, string> = {
  LOW_STOCK: '低库存',
  EXPIRY: '临期',
  OVERSTOCK: '超储',
}

onMounted(loadList)
</script>

<template>
  <el-card shadow="never">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="实时库存" name="list">
        <el-form :inline="true" :model="listQuery">
          <el-form-item label="仓库">
            <el-input v-model="listQuery.warehouseCode" placeholder="仓库编码" clearable />
          </el-form-item>
          <el-form-item label="物料编码">
            <el-input v-model="listQuery.materialCode" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadList">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="listData" stripe>
          <el-table-column prop="materialCode" label="物料编码" min-width="120" />
          <el-table-column prop="labelNo" label="标签号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="specification" label="物料规格" min-width="120" show-overflow-tooltip />
          <el-table-column prop="availableQty" label="可用量" width="110" />
          <el-table-column prop="unitCode" label="单位" width="80" />
          <el-table-column prop="warehouseCode" label="仓库编码" width="110" />
          <el-table-column prop="productionDate" label="生产日期" width="120">
            <template #default="{ row }">
              <WmsDateText :value="row.productionDate" />
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建日期" width="160">
            <template #default="{ row }">
              <WmsDateText :value="row.createTime" />
            </template>
          </el-table-column>
          <el-table-column prop="stockQty" label="库存数量" width="110" />
        </el-table>
        <el-pagination
          v-model:current-page="listQuery.current"
          v-model:page-size="listQuery.size"
          :total="listTotal"
          layout="total, prev, pager, next"
          style="margin-top: 16px"
          @current-change="loadList"
        />
      </el-tab-pane>

      <el-tab-pane label="库存流水" name="txn">
        <el-form :inline="true" :model="txnQuery">
          <el-form-item label="仓库">
            <el-input v-model="txnQuery.warehouseCode" clearable />
          </el-form-item>
          <el-form-item label="物料编码">
            <el-input v-model="txnQuery.materialCode" clearable />
          </el-form-item>
          <el-form-item label="类型">
            <WmsSelect v-model="txnQuery.transactionType" clearable placeholder="全部">
              <el-option label="入库" value="INBOUND" />
              <el-option label="出库" value="OUTBOUND" />
              <el-option label="移库" value="TRANSFER" />
              <el-option label="调整" value="ADJUST" />
            </WmsSelect>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadTransactions">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="txnData" stripe>
          <el-table-column prop="transactionNo" label="流水号" width="150" />
          <el-table-column prop="transactionType" label="类型" width="80">
            <template #default="{ row }">
              {{ TXN_TYPE_MAP[row.transactionType] || row.transactionType }}
            </template>
          </el-table-column>
          <el-table-column prop="warehouseCode" label="仓库" width="90" />
          <el-table-column prop="locationCode" label="库位" width="130" />
          <el-table-column prop="materialCode" label="物料" width="120" />
          <el-table-column prop="batchNo" label="批次" width="110" />
          <el-table-column prop="transactionQty" label="变动数量" width="100" />
          <el-table-column prop="beforeQty" label="变动前" width="90" />
          <el-table-column prop="afterQty" label="变动后" width="90" />
          <el-table-column prop="sourceOrderNo" label="来源单号" width="140" />
          <el-table-column prop="operatorName" label="操作人" width="90" />
          <el-table-column prop="operationTime" label="操作时间" width="120">
            <template #default="{ row }">
              <WmsDateText :value="row.operationTime" />
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="txnQuery.current"
          v-model:page-size="txnQuery.size"
          :total="txnTotal"
          layout="total, prev, pager, next"
          style="margin-top: 16px"
          @current-change="loadTransactions"
        />
      </el-tab-pane>

      <el-tab-pane label="库存预警" name="warn">
        <el-form :inline="true" :model="warnQuery">
          <el-form-item label="仓库">
            <el-input v-model="warnQuery.warehouseCode" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadWarnings">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="warnData" stripe>
          <el-table-column prop="warehouseCode" label="仓库" width="100" />
          <el-table-column prop="materialCode" label="物料编码" width="140" />
          <el-table-column prop="materialName" label="物料名称" min-width="160" />
          <el-table-column prop="currentQty" label="当前库存" width="110" />
          <el-table-column prop="safetyQty" label="安全库存" width="110" />
          <el-table-column prop="warningType" label="预警类型" width="100">
            <template #default="{ row }">
              <el-tag :type="row.warningType === 'LOW_STOCK' ? 'danger' : 'warning'" size="small">
                {{ WARN_TYPE_MAP[row.warningType] || row.warningType }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="warnQuery.current"
          v-model:page-size="warnQuery.size"
          :total="warnTotal"
          layout="total, prev, pager, next"
          style="margin-top: 16px"
          @current-change="loadWarnings"
        />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>
