<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { bindMesPersonnel, getMesPersonnel, type MesPersonnel } from '@/api/mes'
import { formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import DetailShell from '@/components/detail/DetailShell.vue'
import BasicInfoGrid from '@/components/detail/BasicInfoGrid.vue'
import OrderStatusTag from '@/views/components/OrderStatusTag.vue'

const route = useRoute()
const userStore = useUserStore()
const personnelCode = String(route.params.personnelCode)
const loading = ref(false)
const personnel = ref<MesPersonnel | null>(null)

const bindVisible = ref(false)
const bindForm = reactive({ sysUserId: undefined as number | undefined, sysUsername: '' })

const basicItems = computed(() => {
  const p = personnel.value
  if (!p) return []
  return [
    { label: '人员编码', value: p.personnelCode },
    { label: '姓名', value: p.personnelName },
    { label: '部门', value: p.deptName || p.deptCode },
    { label: '岗位', value: p.postName || p.postCode },
    { label: '技能等级', value: p.skillLevel },
    { label: '工作中心', value: p.workCenterCode },
    { label: '生产人员', value: p.productionFlag === 1 ? '是' : '否' },
    { label: '绑定用户', value: p.sysUsername },
    { label: '内码', value: p.erpId },
    { label: '状态', slot: 'status' },
    { label: '同步状态', slot: 'syncStatus' },
    { label: '最后同步', value: formatDateTime(p.lastSyncTime) },
    { label: '失败原因', value: p.failReason, span: 2 },
  ]
})

function openBind() {
  bindForm.sysUserId = personnel.value?.sysUserId
  bindForm.sysUsername = personnel.value?.sysUsername || ''
  bindVisible.value = true
}

async function submitBind(clear = false) {
  const vo = await bindMesPersonnel(personnelCode, clear
    ? { sysUserId: undefined, sysUsername: '' }
    : { sysUserId: bindForm.sysUserId, sysUsername: bindForm.sysUsername })
  personnel.value = vo
  ElMessage.success(clear ? '已解绑' : '绑定成功')
  bindVisible.value = false
}

async function load() {
  loading.value = true
  try {
    personnel.value = await getMesPersonnel(personnelCode)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailShell
    title="人员详情"
    :code="personnel?.personnelCode || personnelCode"
    :status="personnel?.status"
    back-to="/mes/personnel"
  >
    <template #header-extra>
      <el-button v-if="userStore.hasPermission('mes:personnel:bind')" size="small" @click="openBind">
        绑定用户
      </el-button>
    </template>

    <div v-loading="loading">
      <BasicInfoGrid :items="basicItems">
        <template #status>
          <OrderStatusTag :status="personnel?.status" />
        </template>
        <template #syncStatus>
          <OrderStatusTag :status="personnel?.syncStatus" pending-label="未同步" failed-label="同步失败" />
        </template>
      </BasicInfoGrid>
    </div>

    <el-dialog v-model="bindVisible" title="绑定系统用户" width="440px">
      <el-form label-width="110px">
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
  </DetailShell>
</template>
