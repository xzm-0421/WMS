import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { MENU_PERMISSIONS } from '@/utils/permission'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '工作台', permission: MENU_PERMISSIONS['/dashboard'] },
        },
        {
          path: 'system/users',
          name: 'SystemUsers',
          component: () => import('@/views/system/UserList.vue'),
          meta: { title: '用户管理', permission: MENU_PERMISSIONS['/system/users'] },
        },
        {
          path: 'system/roles',
          name: 'SystemRoles',
          component: () => import('@/views/system/RoleList.vue'),
          meta: { title: '角色管理', permission: MENU_PERMISSIONS['/system/roles'] },
        },
        {
          path: 'base/materials',
          name: 'Materials',
          component: () => import('@/views/base/MaterialList.vue'),
          meta: { title: '物料管理', permission: MENU_PERMISSIONS['/base/materials'] },
        },
        {
          path: 'base/warehouses',
          name: 'Warehouses',
          component: () => import('@/views/base/WarehouseList.vue'),
          meta: { title: '仓库管理', permission: MENU_PERMISSIONS['/base/warehouses'] },
        },
        {
          path: 'base/locations',
          name: 'Locations',
          component: () => import('@/views/base/LocationList.vue'),
          meta: { title: '库位管理', permission: MENU_PERMISSIONS['/base/locations'] },
        },
        {
          path: 'inbound/orders',
          name: 'InboundOrders',
          component: () => import('@/views/inbound/InboundOrderList.vue'),
          meta: { title: '入库单', permission: MENU_PERMISSIONS['/inbound/orders'] },
        },
        {
          path: 'inbound/pda-records',
          name: 'PdaInboundRecords',
          component: () => import('@/views/inbound/PdaInboundRecordList.vue'),
          meta: { title: 'PDA入库记录', permission: MENU_PERMISSIONS['/inbound/pda-records'] },
        },
        {
          path: 'inbound/receive-batches',
          name: 'ReceiveBatches',
          component: () => import('@/views/inbound/ReceiveBatchList.vue'),
          meta: { title: '收料入库批次', permission: MENU_PERMISSIONS['/inbound/pda-records'] },
        },
        {
          path: 'outbound/pda-records',
          name: 'PdaOutboundRecords',
          component: () => import('@/views/outbound/PdaOutboundRecordList.vue'),
          meta: { title: 'PDA出库记录', permission: MENU_PERMISSIONS['/outbound/pda-records'] },
        },
        {
          path: 'inventory/list',
          name: 'Inventory',
          component: () => import('@/views/inventory/InventoryList.vue'),
          meta: { title: '实时库存', permission: MENU_PERMISSIONS['/inventory/list'] },
        },
        {
          path: 'inventory/sample-plans',
          name: 'SamplePlans',
          component: () => import('@/views/inventory/SamplePlanList.vue'),
          meta: { title: '库存抽检', permission: MENU_PERMISSIONS['/inventory/sample-plans'] },
        },
        {
          path: 'barcode/rules',
          name: 'BarcodeRules',
          component: () => import('@/views/barcode/RuleList.vue'),
          meta: { title: '条码规则', permission: MENU_PERMISSIONS['/barcode/rules'] },
        },
        {
          path: 'print/label-jobs',
          name: 'LabelPrintJobs',
          component: () => import('@/views/print/LabelPrintJobList.vue'),
          meta: { title: '期初库存', permission: MENU_PERMISSIONS['/print/label-jobs'] },
        },
        {
          path: 'dashboard/warehouse',
          name: 'WarehouseBoard',
          component: () => import('@/views/dashboard/WarehouseBoard.vue'),
          meta: { title: '仓库统计看板', permission: MENU_PERMISSIONS['/dashboard/warehouse'] },
        },
        {
          path: 'system/rules',
          name: 'BusinessRules',
          component: () => import('@/views/system/BusinessRuleList.vue'),
          meta: { title: '业务规则', permission: MENU_PERMISSIONS['/system/rules'] },
        },
        {
          path: 'system/business-flow',
          name: 'BusinessFlow',
          component: () => import('@/views/system/BusinessFlowView.vue'),
          meta: { title: '业务流程手册', permission: MENU_PERMISSIONS['/system/business-flow'] },
        },
        {
          path: 'report/overview',
          name: 'ReportOverview',
          component: () => import('@/views/report/OverviewView.vue'),
          meta: { title: '报表概览', permission: MENU_PERMISSIONS['/report/overview'] },
        },
      ],
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!userStore.token) {
    next('/login')
    return
  }
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      userStore.logout()
      ElMessage.error('登录已失效或服务不可用，请重新登录')
      next('/login')
      return
    }
  }
  const permission = to.meta.permission as string | undefined
  if (permission && !userStore.hasPermission(permission)) {
    ElMessage.warning('无访问权限')
    next('/dashboard')
    return
  }
  next()
})

export default router
