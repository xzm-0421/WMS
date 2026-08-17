<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createOtherInbound, getOtherInbounds, type OtherInbound } from '@/api/inventoryExt'
import MaterialSelectInput from '@/components/MaterialSelectInput.vue'
import { applyMaterialBasic, type Material } from '@/api/material'
import PrintActions from '@/components/PrintActions.vue'
import { getDictLabel } from '@/utils/dict'

const loading = ref(false)
const tableData = ref<OtherInbound[]>([])
const total = ref(0)
const query = reactive({ orderNo: '', inboundType: '', current: 1, size: 20 })
const dialogVisible = ref(false)
const form = reactive({
  inboundType: 'RETURN',
  warehouseCode: 'WH001',
  sourceDesc: '退货入库',
  lines: [{ materialCode: '', materialName: '', quantity: 1, locationCode: 'LOC001', batchNo: 'B001' }],
})

function onMaterialSelect(material: Material) {
  applyMaterialBasic(form.lines[0] as unknown as Record<string, unknown>, material)
}

async function loadData() {
  loading.value = true
  try {
    const res = await getOtherInbounds(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  await createOtherInbound(form)
  ElMessage.success('其他入库单已创建')
  dialogVisible.value = false
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true">
      <el-form-item label="单号"><el-input v-model="query.orderNo" clearable /></el-form-item>
      <el-form-item label="类型">
        <WmsSelect v-model="query.inboundType" clearable>
          <el-option label="退货入库" value="RETURN" />
          <el-option label="赠品入库" value="GIFT" />
          <el-option label="借还入库" value="BORROW" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="dialogVisible = true">快速入库</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="orderNo" label="单号" width="160" />
      <el-table-column prop="inboundType" label="类型" width="100">
        <template #default="{ row }">
          {{ getDictLabel('inboundType', row.inboundType) }}
        </template>
      </el-table-column>
      <el-table-column prop="warehouseCode" label="仓库" width="100" />
      <el-table-column prop="sourceDesc" label="来源" min-width="160" />
      <el-table-column prop="creatorName" label="操作人" width="100" />
      <el-table-column label="打印" width="140" fixed="right">
        <template #default="{ row }">
          <PrintActions biz="other_inbound" :doc-no="row.orderNo" />
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" title="其他入库" width="480px">
    <el-form label-width="90px">
      <el-form-item label="类型">
        <WmsSelect v-model="form.inboundType">
          <el-option label="退货入库" value="RETURN" />
          <el-option label="赠品入库" value="GIFT" />
          <el-option label="借还入库" value="BORROW" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="仓库"><el-input v-model="form.warehouseCode" /></el-form-item>
      <el-form-item label="来源说明"><el-input v-model="form.sourceDesc" /></el-form-item>
      <el-form-item label="物料">
        <MaterialSelectInput v-model="form.lines[0]!.materialCode" @select="onMaterialSelect" />
      </el-form-item>
      <el-form-item label="物料名称">
        <el-input v-model="form.lines[0]!.materialName" readonly placeholder="选择物料后自动带出" />
      </el-form-item>
      <el-form-item label="数量"><el-input-number v-model="form.lines[0]!.quantity" :min="0.001" :precision="4" :step="0.001" /></el-form-item>
      <el-form-item label="库位"><el-input v-model="form.lines[0]!.locationCode" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleCreate">提交</el-button>
    </template>
  </el-dialog>
</template>
