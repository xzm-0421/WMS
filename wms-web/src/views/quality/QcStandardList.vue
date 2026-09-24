<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getQcStandards, createQcStandard, type QcStandard } from '@/api/quality'

const loading = ref(false)
const tableData = ref<QcStandard[]>([])
const total = ref(0)
const query = reactive({ materialCode: '', qcType: '', current: 1, size: 20 })

const dialogVisible = ref(false)
const form = reactive({
  standardName: '',
  materialCode: '',
  materialName: '',
  qcType: 'INCOMING',
  qcItems: '',
})

async function loadData() {
  loading.value = true
  try {
    const res = await getQcStandards(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  Object.assign(form, {
    standardName: '',
    materialCode: '',
    materialName: '',
    qcType: 'INCOMING',
    qcItems: '',
  })
  dialogVisible.value = true
}

async function handleCreate() {
  await createQcStandard(form)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="物料编码">
        <el-input v-model="query.materialCode" clearable />
      </el-form-item>
      <el-form-item label="质检类型">
        <WmsSelect v-model="query.qcType" clearable placeholder="全部">
          <el-option label="来料质检" value="INCOMING" />
          <el-option label="过程质检" value="PROCESS" />
          <el-option label="成品质检" value="FINAL" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="handleAdd">新建标准</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="standardCode" label="标准编码" width="160" />
      <el-table-column prop="standardName" label="标准名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="qcType" label="质检类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.qcType === 'INCOMING'" type="primary" size="small">来料</el-tag>
          <el-tag v-else-if="row.qcType === 'PROCESS'" type="warning" size="small">过程</el-tag>
          <el-tag v-else-if="row.qcType === 'FINAL'" type="success" size="small">成品</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="checkItems" label="检验项目" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success" size="small">启用</el-tag>
          <el-tag v-else type="info" size="small">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170">
        <template #default="{ row }">
          <WmsDateText :value="row.createTime" />
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

  <el-dialog v-model="dialogVisible" title="新建质检标准" width="600px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="标准名称">
        <el-input v-model="form.standardName" placeholder="留空则自动生成" />
      </el-form-item>
      <el-form-item label="物料编码" required>
        <el-input v-model="form.materialCode" />
      </el-form-item>
      <el-form-item label="物料名称">
        <el-input v-model="form.materialName" />
      </el-form-item>
      <el-form-item label="质检类型" required>
        <WmsSelect v-model="form.qcType">
          <el-option label="来料质检" value="INCOMING" />
          <el-option label="过程质检" value="PROCESS" />
          <el-option label="成品质检" value="FINAL" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="检验项目" required>
        <el-input
          v-model="form.qcItems"
          type="textarea"
          :rows="4"
          placeholder="每行一个检验项目，格式：项目名称|标准值|公差"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleCreate">确定</el-button>
    </template>
  </el-dialog>
</template>
