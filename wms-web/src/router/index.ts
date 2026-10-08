import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { canAccessPath, MENU_PERMISSIONS } from '@/utils/permission'

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
          path: 'system/operation-logs',
          name: 'SystemOperationLogs',
          component: () => import('@/views/system/OperationLogList.vue'),
          meta: { title: '操作日志', permission: MENU_PERMISSIONS['/system/operation-logs'] },
        },
        {
          path: 'system/alerts',
          name: 'SystemAlerts',
          component: () => import('@/views/system/AlertList.vue'),
          meta: { title: '告警中心', permission: MENU_PERMISSIONS['/system/alerts'] },
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
          path: 'outbound/orders',
          name: 'OutboundOrders',
          component: () => import('@/views/outbound/OutboundOrderList.vue'),
          meta: { title: '出库单', permission: MENU_PERMISSIONS['/outbound/orders'] },
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
          path: 'inventory/transfers',
          name: 'InventoryTransfers',
          component: () => import('@/views/inventory/TransferList.vue'),
          meta: { title: '移库管理', permission: MENU_PERMISSIONS['/inventory/transfers'] },
        },
        {
          path: 'barcode/rules',
          name: 'BarcodeRules',
          component: () => import('@/views/barcode/RuleList.vue'),
          meta: { title: '条码规则', permission: MENU_PERMISSIONS['/barcode/rules'] },
        },
        {
          path: 'stocktake/plans',
          name: 'StocktakePlans',
          component: () => import('@/views/stocktake/PlanList.vue'),
          meta: { title: '盘点计划', permission: MENU_PERMISSIONS['/stocktake/plans'] },
        },
        {
          path: 'stocktake/tasks',
          name: 'StocktakeTasks',
          component: () => import('@/views/stocktake/TaskList.vue'),
          meta: { title: '盘点任务', permission: MENU_PERMISSIONS['/stocktake/tasks'] },
        },
        {
          path: 'stocktake/diffs',
          name: 'StocktakeDiffs',
          component: () => import('@/views/stocktake/DiffList.vue'),
          meta: { title: '盘点差异', permission: MENU_PERMISSIONS['/stocktake/diffs'] },
        },
        {
          path: 'quality/standards',
          name: 'QualityStandards',
          component: () => import('@/views/quality/QcStandardList.vue'),
          meta: { title: '质检标准', permission: MENU_PERMISSIONS['/quality/standards'] },
        },
        {
          path: 'quality/orders',
          name: 'QualityOrders',
          component: () => import('@/views/quality/QcOrderList.vue'),
          meta: { title: '质检单', permission: MENU_PERMISSIONS['/quality/orders'] },
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
        {
          path: 'mes/boms',
          name: 'MesBoms',
          component: () => import('@/views/mes/BomList.vue'),
          meta: { title: 'BOM管理', permission: MENU_PERMISSIONS['/mes/boms'] },
        },
        {
          path: 'mes/process',
          name: 'MesProcess',
          component: () => import('@/views/mes/ProcessList.vue'),
          meta: { title: '工序管理', permission: MENU_PERMISSIONS['/mes/process'] },
        },
        {
          path: 'mes/equipment',
          name: 'MesEquipment',
          component: () => import('@/views/mes/EquipmentList.vue'),
          meta: { title: '设备管理', permission: MENU_PERMISSIONS['/mes/equipment'] },
        },
        {
          path: 'mes/routes',
          name: 'MesRoutes',
          component: () => import('@/views/mes/RouteList.vue'),
          meta: { title: '工艺路线', permission: MENU_PERMISSIONS['/mes/routes'] },
        },
        {
          path: 'mes/work-centers',
          name: 'MesWorkCenters',
          component: () => import('@/views/mes/WorkCenterList.vue'),
          meta: { title: '工作中心', permission: MENU_PERMISSIONS['/mes/work-centers'] },
        },
        {
          path: 'mes/resources',
          name: 'MesResources',
          component: () => import('@/views/mes/ResourceList.vue'),
          meta: { title: '资源管理', permission: MENU_PERMISSIONS['/mes/resources'] },
        },
        {
          path: 'mes/personnel',
          name: 'MesPersonnel',
          component: () => import('@/views/mes/PersonnelList.vue'),
          meta: { title: '人员管理', permission: MENU_PERMISSIONS['/mes/personnel'] },
        },
        {
          path: 'mes/master',
          redirect: '/base/materials',
        },
        {
          path: 'mes/plans',
          name: 'MesPlans',
          component: () => import('@/views/mes/OpPlanList.vue'),
          meta: { title: '工序计划', permission: MENU_PERMISSIONS['/mes/plans'] },
        },
        {
          path: 'mes/report',
          name: 'MesReport',
          component: () => import('@/views/mes/ReportSubmit.vue'),
          meta: { title: '工序报工', permission: MENU_PERMISSIONS['/mes/report'] },
        },
        {
          path: 'mes/transfer',
          name: 'MesTransfer',
          component: () => import('@/views/mes/TransferSubmit.vue'),
          meta: { title: '工序转移', permission: MENU_PERMISSIONS['/mes/transfer'] },
        },
        {
          path: 'mes/transfers',
          name: 'MesTransfers',
          component: () => import('@/views/mes/TransferList.vue'),
          meta: { title: '工序转移记录', permission: MENU_PERMISSIONS['/mes/transfers'] },
        },
        {
          path: 'mes/rework',
          name: 'MesRework',
          component: () => import('@/views/mes/ReworkView.vue'),
          meta: { title: '不良与返工', permission: MENU_PERMISSIONS['/mes/rework'] },
        },
        {
          path: 'mes/reports',
          name: 'MesReports',
          component: () => import('@/views/mes/ReportList.vue'),
          meta: { title: '报工记录', permission: MENU_PERMISSIONS['/mes/reports'] },
        },
        {
          path: 'mes/sync',
          name: 'MesSync',
          component: () => import('@/views/mes/SyncCenter.vue'),
          meta: { title: '同步中心', permission: MENU_PERMISSIONS['/mes/sync'] },
        },
      ],
    },
    {
      path: '/mes/plans/:id',
      name: 'MesPlanDetail',
      component: () => import('@/views/mes/detail/OpPlanDetail.vue'),
      meta: { title: '工序计划详情', permission: MENU_PERMISSIONS['/mes/plans'] },
    },
    {
      path: '/mes/routes/:id',
      name: 'MesRouteDetail',
      component: () => import('@/views/mes/detail/RouteDetail.vue'),
      meta: { title: '工艺路线详情', permission: MENU_PERMISSIONS['/mes/routes'] },
    },
    {
      path: '/mes/process/:processCode',
      name: 'MesProcessDetail',
      component: () => import('@/views/mes/detail/ProcessDetail.vue'),
      meta: { title: '工序详情', permission: MENU_PERMISSIONS['/mes/process'] },
    },
    {
      path: '/mes/equipment/:equipmentCode',
      name: 'MesEquipmentDetail',
      component: () => import('@/views/mes/detail/EquipmentDetail.vue'),
      meta: { title: '设备详情', permission: MENU_PERMISSIONS['/mes/equipment'] },
    },
    {
      path: '/mes/reports/:reportNo',
      name: 'MesReportDetail',
      component: () => import('@/views/mes/detail/ReportDetail.vue'),
      meta: { title: '报工记录详情', permission: MENU_PERMISSIONS['/mes/reports'] },
    },
    {
      path: '/mes/transfers/:transferNo',
      name: 'MesTransferDetail',
      component: () => import('@/views/mes/detail/TransferDetail.vue'),
      meta: { title: '工序转移详情', permission: MENU_PERMISSIONS['/mes/transfers'] },
    },
    {
      path: '/mes/rework/:defectNo',
      name: 'MesReworkDetail',
      component: () => import('@/views/mes/detail/ReworkDetail.vue'),
      meta: { title: '不良/返工详情', permission: MENU_PERMISSIONS['/mes/rework'] },
    },
    {
      path: '/mes/work-centers/:workCenterCode',
      name: 'MesWorkCenterDetail',
      component: () => import('@/views/mes/detail/WorkCenterDetail.vue'),
      meta: { title: '工作中心详情', permission: MENU_PERMISSIONS['/mes/work-centers'] },
    },
    {
      path: '/mes/resources/:resourceCode',
      name: 'MesResourceDetail',
      component: () => import('@/views/mes/detail/ResourceDetail.vue'),
      meta: { title: '资源详情', permission: MENU_PERMISSIONS['/mes/resources'] },
    },
    {
      path: '/mes/personnel/:personnelCode',
      name: 'MesPersonnelDetail',
      component: () => import('@/views/mes/detail/PersonnelDetail.vue'),
      meta: { title: '人员详情', permission: MENU_PERMISSIONS['/mes/personnel'] },
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
  if (permission && !canAccessPath(to.path, userStore.permissions, userStore.roles)) {
    ElMessage.warning('无访问权限')
    next('/dashboard')
    return
  }
  next()
})

export default router
