<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignRolePermissions,
  createRole,
  deleteRole,
  getRole,
  getRoles,
  updateRole,
  type SysRole,
} from '@/api/role'
import { getPermissionModules, type PermissionModule } from '@/api/permission'
import { getWarehouses } from '@/api/warehouse'
import { useUserStore } from '@/stores/user'
import { DATA_SCOPE_LABELS } from '@/utils/permission'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<SysRole[]>([])
const total = ref(0)
const query = reactive({ roleCode: '', roleName: '', current: 1, size: 20 })

const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const editingId = ref<number | null>(null)
const form = reactive<SysRole>({
  roleCode: '',
  roleName: '',
  description: '',
  status: 1,
})

const permDialogVisible = ref(false)
const permLoading = ref(false)
const permRole = ref<SysRole | null>(null)
const permissionModules = ref<PermissionModule[]>([])
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const checkedPermissions = ref<string[]>([])
const permForm = reactive({
  dataScope: 2,
  warehouseScope: [] as string[],
})

const isSuperAdmin = computed(() => userStore.isSuperAdmin)

function resetForm() {
  Object.assign(form, {
    roleCode: '',
    roleName: '',
    description: '',
    status: 1,
  })
  editingId.value = null
}

function dataScopeLabel(scope?: number) {
  if (!scope) return '-'
  return DATA_SCOPE_LABELS[scope] ?? '未知'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getRoles(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadWarehouses() {
  const res = await getWarehouses({ current: 1, size: 200 })
  warehouseOptions.value = res.records.map((w) => ({
    label: `${w.warehouseName} (${w.warehouseCode})`,
    value: w.warehouseCode,
  }))
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增角色'
  dialogVisible.value = true
}

function handleEdit(row: SysRole) {
  resetForm()
  editingId.value = row.id!
  Object.assign(form, {
    roleCode: row.roleCode,
    roleName: row.roleName,
    description: row.description,
    status: row.status,
  })
  dialogTitle.value = '编辑角色'
  dialogVisible.value = true
}

async function handleSave() {
  if (editingId.value) {
    await updateRole(editingId.value, form)
    ElMessage.success('更新成功')
  } else {
    await createRole(form)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  loadData()
}

async function handleDelete(row: SysRole) {
  if (row.roleCode === 'SUPER_ADMIN') {
    ElMessage.warning('超级管理员角色不可删除')
    return
  }
  await ElMessageBox.confirm(`确定删除角色 ${row.roleName}？`, '提示')
  await deleteRole(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

async function openPermissionDialog(row: SysRole) {
  if (row.roleCode === 'SUPER_ADMIN') {
    ElMessage.info('超级管理员拥有全部权限，无需配置')
    return
  }
  permLoading.value = true
  permDialogVisible.value = true
  permRole.value = row
  try {
    if (!permissionModules.value.length) {
      permissionModules.value = await getPermissionModules()
    }
    const detail = await getRole(row.id!)
    checkedPermissions.value = [...(detail.permissionCodes ?? [])]
    permForm.dataScope = detail.dataScope ?? 2
    permForm.warehouseScope = [...(detail.warehouseScope ?? [])]
  } finally {
    permLoading.value = false
  }
}

async function savePermissions() {
  if (!permRole.value?.id) return
  if (permForm.dataScope === 2 && permForm.warehouseScope.length === 0) {
    ElMessage.warning('指定仓库模式下请至少选择一个仓库')
    return
  }
  await assignRolePermissions(permRole.value.id, {
    permissionCodes: checkedPermissions.value,
    dataScope: permForm.dataScope,
    warehouseScope: permForm.dataScope === 2 ? permForm.warehouseScope : [],
  })
  ElMessage.success('权限与数据范围已保存')
  permDialogVisible.value = false
  loadData()
}

onMounted(() => {
  loadData()
  if (isSuperAdmin.value) {
    loadWarehouses()
  }
})
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" :model="query">
      <el-form-item label="角色编码">
        <el-input v-model="query.roleCode" clearable />
      </el-form-item>
      <el-form-item label="角色名称">
        <el-input v-model="query.roleName" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button v-if="userStore.hasPermission('system:role:add')" @click="handleAdd">
          新增
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="roleCode" label="角色编码" width="140" />
      <el-table-column prop="roleName" label="角色名称" width="160" />
      <el-table-column prop="description" label="描述" min-width="180" />
      <el-table-column prop="dataScope" label="数据范围" width="120">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ dataScopeLabel(row.dataScope) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="仓库范围" min-width="160">
        <template #default="{ row }">
          <template v-if="row.dataScope === 2 && row.warehouseScope?.length">
            <el-tag
              v-for="code in row.warehouseScope"
              :key="code"
              size="small"
              style="margin-right: 4px"
            >
              {{ code }}
            </el-tag>
          </template>
          <span v-else-if="row.dataScope === 1">全部仓库</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <WmsStatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('system:role:edit')"
            link
            type="primary"
            @click="handleEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="isSuperAdmin && row.roleCode !== 'SUPER_ADMIN'"
            link
            type="warning"
            @click="openPermissionDialog(row)"
          >
            权限配置
          </el-button>
          <el-button
            v-if="userStore.hasPermission('system:role:delete') && row.roleCode !== 'SUPER_ADMIN'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
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

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="角色编码" required>
        <el-input v-model="form.roleCode" :disabled="!!editingId" />
      </el-form-item>
      <el-form-item label="角色名称" required>
        <el-input v-model="form.roleName" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-alert
        v-if="!isSuperAdmin"
        type="info"
        :closable="false"
        show-icon
        title="数据范围与功能权限仅超级管理员可在「权限配置」中设置"
      />
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="permDialogVisible"
    :title="`权限配置 - ${permRole?.roleName ?? ''}`"
    width="720px"
    destroy-on-close
  >
    <div v-loading="permLoading">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
        title="仅超级管理员可配置。未勾选的功能模块，该角色用户将无法访问对应菜单与接口。"
      />

      <el-form label-width="100px">
        <el-form-item label="数据范围" required>
          <el-radio-group v-model="permForm.dataScope">
            <el-radio :value="1">全部数据</el-radio>
            <el-radio :value="2">指定仓库</el-radio>
            <el-radio :value="3">本部门</el-radio>
            <el-radio :value="4">本部门及以下</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="permForm.dataScope === 2" label="仓库范围" required>
          <WmsSelect
            v-model="permForm.warehouseScope"
            multiple
            filterable
            block
            placeholder="选择可访问仓库"
          >
            <el-option
              v-for="item in warehouseOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </WmsSelect>
        </el-form-item>

        <el-divider content-position="left">功能权限</el-divider>

        <div class="perm-groups">
          <div v-for="group in permissionModules" :key="group.module" class="perm-group">
            <div class="perm-group-title">{{ group.moduleName }}</div>
            <el-checkbox-group v-model="checkedPermissions">
              <el-checkbox
                v-for="perm in group.permissions"
                :key="perm.permissionCode"
                :value="perm.permissionCode"
                :label="perm.permissionCode"
              >
                {{ perm.permissionName }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </div>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="permDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="savePermissions">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.perm-groups {
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 16px;
}

.perm-group + .perm-group {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
}

.perm-group-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.text-muted {
  color: #909399;
}
</style>
