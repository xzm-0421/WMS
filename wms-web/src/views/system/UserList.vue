<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUsers,
  createUser,
  updateUser,
  deleteUser,
  resetPassword,
  updateUserStatus,
  type SysUser,
} from '@/api/user'

const loading = ref(false)
const tableData = ref<SysUser[]>([])
const total = ref(0)
const query = reactive({ username: '', realName: '', current: 1, size: 20 })

const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const editingId = ref<number | null>(null)
const form = reactive<SysUser>({
  username: '',
  realName: '',
  phone: '',
  email: '',
  status: 1,
  password: '',
})

const resetDialogVisible = ref(false)
const resetUserId = ref<number | null>(null)
const newPassword = ref('')

function resetForm() {
  Object.assign(form, {
    username: '',
    realName: '',
    phone: '',
    email: '',
    status: 1,
    password: '',
  })
  editingId.value = null
}

async function loadData() {
  loading.value = true
  try {
    const res = await getUsers(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增用户'
  dialogVisible.value = true
}

function handleEdit(row: SysUser) {
  resetForm()
  editingId.value = row.id!
  Object.assign(form, { ...row, password: '' })
  dialogTitle.value = '编辑用户'
  dialogVisible.value = true
}

async function handleSave() {
  if (editingId.value) {
    await updateUser(editingId.value, form)
    ElMessage.success('更新成功')
  } else {
    await createUser(form)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  loadData()
}

async function handleDelete(row: SysUser) {
  await ElMessageBox.confirm(`确定删除用户 ${row.username}？`, '提示')
  await deleteUser(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

function handleResetPwd(row: SysUser) {
  resetUserId.value = row.id!
  newPassword.value = ''
  resetDialogVisible.value = true
}

async function confirmResetPwd() {
  if (!newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  await resetPassword(resetUserId.value!, newPassword.value)
  ElMessage.success('密码已重置')
  resetDialogVisible.value = false
}

async function toggleStatus(row: SysUser) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'
  await ElMessageBox.confirm(`确定${action}用户 ${row.username}？`, '提示')
  await updateUserStatus(row.id!, newStatus)
  ElMessage.success(`${action}成功`)
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="用户名">
        <el-input v-model="query.username" clearable />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="query.realName" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="handleAdd">新增</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="phone" label="手机" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <WmsStatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="handleResetPwd(row)">重置密码</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
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
  </el-card>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="用户名" required>
        <el-input v-model="form.username" :disabled="!!editingId" />
      </el-form-item>
      <el-form-item label="姓名" required>
        <el-input v-model="form.realName" />
      </el-form-item>
      <el-form-item v-if="!editingId" label="密码" required>
        <el-input v-model="form.password" type="password" show-password />
      </el-form-item>
      <el-form-item label="手机">
        <el-input v-model="form.phone" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="resetDialogVisible" title="重置密码" width="400px">
    <el-form label-width="80px">
      <el-form-item label="新密码" required>
        <el-input v-model="newPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="resetDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmResetPwd">确定</el-button>
    </template>
  </el-dialog>
</template>
