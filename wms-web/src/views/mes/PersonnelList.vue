<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  bindMesPersonnel,
  getMesPersonnels,
  refreshMesPersonnels,
  type MesPersonnel,
} from '@/api/mes'
import { useUserStore } from '@/stores/user'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const userStore = useUserStore()
const canBind = () => userStore.hasPermission('mes:personnel:bind')
const loading = ref(false)
const syncing = ref(false)
const tableData = ref<MesPersonnel[]>([])
const total = ref(0)
const query = reactive({
  personnelCode: '',
  personnelName: '',
  deptCode: '',
  status: undefined as number | undefined,
  current: 1,
  size: 20,
})

const bindVisible = ref(false)
const bindingCode = ref('')
const bindingName = ref('')
const bindForm = reactive({ sysUserId: undefined as number | undefined, sysUsername: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await getMesPersonnels(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  await ElMessageBox.confirm('将从金蝶ERP拉取人员到本地（本地绑定字段不被覆盖），是否继续？', '从金蝶同步人员')
  syncing.value = true
  try {
    const result = await refreshMesPersonnels()
    ElMessage[result.success ? 'success' : 'warning'](result.message || '同步完成')
    await loadData()
  } finally {
    syncing.value = false
  }
}

function openBind(row: MesPersonnel) {
  bindingCode.value = row.personnelCode || ''
  bindingName.value = row.personnelName || ''
  bindForm.sysUserId = row.sysUserId
  bindForm.sysUsername = row.sysUsername || ''
  bindVisible.value = true
}

async function submitBind(clear = false) {
  await bindMesPersonnel(bindingCode.value, clear
    ? { sysUserId: undefined, sysUsername: '' }
    : { sysUserId: bindForm.sysUserId, sysUsername: bindForm.sysUsername })
  ElMessage.success(clear ? '已解绑' : '绑定成功')
  bindVisible.value = false
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-alert
      title="人员以金蝶ERP为权威源（同步只读）；仅“绑定系统用户”为本地可编辑字段。"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-form :inline="true" :model="query">
      <el-form-item label="人员编码">
        <el-input v-model="query.personnelCode" clearable />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="query.personnelName" clearable />
      </el-form-item>
      <el-form-item label="部门">
        <el-input v-model="query.deptCode" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button
          v-if="userStore.hasPermission('mes:personnel:sync') || userStore.hasPermission('mes:master:sync')"
          type="success"
          :loading="syncing"
          @click="handleSync"
        >
          从金蝶同步
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column label="人员编码" width="140">
        <template #default="{ row }">
          <RouterLink class="detail-link" :to="`/mes/personnel/${encodeURIComponent(row.personnelCode)}`">
            {{ row.personnelCode }}
          </RouterLink>
        </template>
      </el-table-column>
      <el-table-column prop="personnelName" label="姓名" width="120" />
      <el-table-column prop="deptName" label="部门" width="140" show-overflow-tooltip />
      <el-table-column prop="postName" label="岗位" width="120" show-overflow-tooltip />
      <el-table-column prop="workCenterCode" label="工作中心" width="130" show-overflow-tooltip />
      <el-table-column label="生产人员" width="90">
        <template #default="{ row }">{{ row.productionFlag === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="sysUsername" label="绑定用户" width="120">
        <template #default="{ row }">{{ row.sysUsername || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><WmsStatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="同步" width="110">
        <template #default="{ row }">
          <OrderStatusTag :status="row.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canBind()" link type="primary" @click="openBind(row)">绑定用户</el-button>
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

  <el-dialog v-model="bindVisible" title="绑定系统用户" width="440px">
    <el-form label-width="110px">
      <el-form-item label="人员">
        <span>{{ bindingName }} ({{ bindingCode }})</span>
      </el-form-item>
      <el-form-item label="系统用户ID">
        <el-input-number v-model="bindForm.sysUserId" :min="0" :controls="false" style="width: 100%" />
      </el-form-item>
      <el-form-item label="系统用户名">
        <el-input v-model="bindForm.sysUsername" clearable placeholder="登录账号" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="danger" plain @click="submitBind(true)">解绑</el-button>
      <el-button @click="bindVisible = false">取消</el-button>
      <el-button type="primary" @click="submitBind(false)">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.detail-link {
  color: var(--el-color-primary);
}
</style>
