<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { canAccessPath, MENU_PERMISSIONS } from '@/utils/permission'
import {
  ArrowDown,
  Box,
  Document,
  Goods,
  Grid,
  Odometer,
  Setting,
  TrendCharts,
  Cpu,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const defaultOpeneds = ref(['system', 'base', 'warehouse', 'inventory', 'stocktake', 'qc', 'dashboard', 'mes'])

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => {
  const items: { title: string; path?: string }[] = [{ title: '首页', path: '/dashboard' }]
  if (route.meta.title && route.path !== '/dashboard') {
    items.push({ title: String(route.meta.title) })
  } else {
    items.push({ title: '工作台' })
  }
  return items
})

const menus = [
  { path: '/dashboard', title: '工作台', icon: Odometer, permission: MENU_PERMISSIONS['/dashboard'] },
  {
    index: 'system',
    title: '系统管理',
    icon: Setting,
    children: [
      { path: '/system/users', title: '用户管理', permission: MENU_PERMISSIONS['/system/users'] },
      { path: '/system/roles', title: '角色管理', permission: MENU_PERMISSIONS['/system/roles'] },
      { path: '/system/rules', title: '业务规则', permission: MENU_PERMISSIONS['/system/rules'] },
      { path: '/system/business-flow', title: '业务流程手册', permission: MENU_PERMISSIONS['/system/business-flow'] },
    ],
  },
  {
    index: 'base',
    title: '基础数据',
    icon: Grid,
    children: [
      { path: '/base/materials', title: '产品/物料', permission: MENU_PERMISSIONS['/base/materials'] },
      { path: '/base/warehouses', title: '仓库信息', permission: MENU_PERMISSIONS['/base/warehouses'] },
      { path: '/base/locations', title: '库位信息', permission: MENU_PERMISSIONS['/base/locations'] },
      { path: '/barcode/rules', title: '条码规则', permission: MENU_PERMISSIONS['/barcode/rules'] },
      { path: '/print/label-jobs', title: '期初库存', permission: MENU_PERMISSIONS['/print/label-jobs'] },
    ],
  },
  {
    index: 'warehouse',
    title: '出入库管理',
    icon: Document,
    children: [
      { path: '/inbound/orders', title: '入库单', permission: MENU_PERMISSIONS['/inbound/orders'] },
      { path: '/inbound/pda-records', title: 'PDA入库记录', permission: MENU_PERMISSIONS['/inbound/pda-records'] },
      { path: '/inbound/receive-batches', title: '收料入库批次', permission: MENU_PERMISSIONS['/inbound/pda-records'] },
      { path: '/outbound/orders', title: '出库单', permission: MENU_PERMISSIONS['/outbound/orders'] },
      { path: '/outbound/pda-records', title: 'PDA出库记录', permission: MENU_PERMISSIONS['/outbound/pda-records'] },
    ],
  },
  {
    index: 'inventory',
    title: '库存管理',
    icon: Goods,
    children: [
      { path: '/inventory/list', title: '实时库存', permission: MENU_PERMISSIONS['/inventory/list'] },
      { path: '/inventory/sample-plans', title: '库存抽检', permission: MENU_PERMISSIONS['/inventory/sample-plans'] },
      { path: '/inventory/transfers', title: '移库管理', permission: MENU_PERMISSIONS['/inventory/transfers'] },
    ],
  },
  {
    index: 'stocktake',
    title: '盘点管理',
    icon: Grid,
    children: [
      { path: '/stocktake/plans', title: '盘点计划', permission: MENU_PERMISSIONS['/stocktake/plans'] },
      { path: '/stocktake/tasks', title: '盘点任务', permission: MENU_PERMISSIONS['/stocktake/tasks'] },
      { path: '/stocktake/diffs', title: '盘点差异', permission: MENU_PERMISSIONS['/stocktake/diffs'] },
    ],
  },
  {
    index: 'qc',
    title: '质检管理',
    icon: Document,
    children: [
      { path: '/quality/standards', title: '质检标准', permission: MENU_PERMISSIONS['/quality/standards'] },
      { path: '/quality/orders', title: '质检单', permission: MENU_PERMISSIONS['/quality/orders'] },
    ],
  },
  {
    index: 'mes',
    title: '轻MES',
    icon: Cpu,
    children: [
      { path: '/mes/materials', title: '物料管理', permission: MENU_PERMISSIONS['/mes/materials'] },
      { path: '/mes/boms', title: 'BOM管理', permission: MENU_PERMISSIONS['/mes/boms'] },
      { path: '/mes/process', title: '工序管理', permission: MENU_PERMISSIONS['/mes/process'] },
      { path: '/mes/equipment', title: '设备管理', permission: MENU_PERMISSIONS['/mes/equipment'] },
      { path: '/mes/routes', title: '工艺路线', permission: MENU_PERMISSIONS['/mes/routes'] },
      { path: '/mes/plans', title: '工序计划', permission: MENU_PERMISSIONS['/mes/plans'] },
      { path: '/mes/report', title: '工序报工', permission: MENU_PERMISSIONS['/mes/report'] },
      { path: '/mes/transfer', title: '工序转移', permission: MENU_PERMISSIONS['/mes/transfer'] },
      { path: '/mes/rework', title: '不良与返工', permission: MENU_PERMISSIONS['/mes/rework'] },
      { path: '/mes/reports', title: '报工记录', permission: MENU_PERMISSIONS['/mes/reports'] },
      { path: '/mes/sync', title: '同步中心', permission: MENU_PERMISSIONS['/mes/sync'] },
    ],
  },
  {
    index: 'dashboard',
    title: '数据看板',
    icon: TrendCharts,
    children: [
      { path: '/dashboard', title: '工作台', permission: MENU_PERMISSIONS['/dashboard'] },
      { path: '/dashboard/warehouse', title: '仓库统计看板', permission: MENU_PERMISSIONS['/dashboard/warehouse'] },
      { path: '/report/overview', title: '报表概览', permission: MENU_PERMISSIONS['/report/overview'] },
    ],
  },
]

const visibleMenus = computed(() => {
  const perms = userStore.permissions
  const roles = userStore.roles
  return menus
    .map((item) => {
      if (item.path) {
        return canAccessPath(item.path, perms, roles) ? item : null
      }
      const children = item.children?.filter((child) =>
        canAccessPath(child.path, perms, roles)
      )
      if (!children?.length) return null
      return { ...item, children }
    })
    .filter(Boolean) as typeof menus
})

onMounted(() => {
  if (userStore.token && !userStore.userInfo) {
    userStore.fetchUserInfo()
  }
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="wms-layout">
    <el-aside :width="'var(--wms-sidebar-width)'" class="wms-aside">
      <div class="wms-logo">
        <el-icon class="logo-icon"><Box /></el-icon>
        <span class="logo-text">WMS管理系统</span>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="activeMenu"
          :default-openeds="defaultOpeneds"
          router
          class="wms-menu"
        >
          <template v-for="item in visibleMenus" :key="item.path || item.index">
            <el-menu-item v-if="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>

            <el-sub-menu v-else :index="item.index!">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item
                v-for="child in item.children"
                :key="child.path"
                :index="child.path"
              >
                {{ child.title }}
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="wms-main-wrap">
      <el-header class="wms-header" height="var(--wms-header-height)">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item
            v-for="(crumb, idx) in breadcrumbs"
            :key="idx"
            :to="crumb.path ? { path: crumb.path } : undefined"
          >
            {{ crumb.title }}
          </el-breadcrumb-item>
        </el-breadcrumb>

        <el-dropdown trigger="click" @command="(cmd: string) => cmd === 'logout' && handleLogout()">
          <div class="user-trigger">
            <el-avatar :size="32" class="user-avatar">
              <el-icon><TrendCharts /></el-icon>
            </el-avatar>
            <span class="user-name">{{ userStore.userInfo?.realName || '管理员' }}</span>
            <el-icon class="user-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="wms-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.wms-layout {
  height: 100vh;
  overflow: hidden;
}

.wms-aside {
  background: var(--wms-sidebar-bg);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--wms-sidebar-border);
  transition: width var(--wms-transition-normal);
}

.wms-logo {
  height: var(--wms-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid var(--wms-sidebar-border);
  flex-shrink: 0;
}

.logo-icon {
  font-size: 22px;
  color: var(--wms-sidebar-active-text);
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.menu-scroll {
  flex: 1;
}

.wms-menu {
  border-right: none;
  background: transparent;
  padding: 8px 0 16px;
}

.wms-aside :deep(.el-menu) {
  background: transparent;
}

.wms-aside :deep(.el-menu-item),
.wms-aside :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  margin: 2px 10px;
  border-radius: 6px;
  color: var(--wms-sidebar-text);
  transition:
    background var(--wms-transition-fast),
    color var(--wms-transition-fast);
}

.wms-aside :deep(.el-menu-item:hover),
.wms-aside :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.06);
  color: var(--wms-sidebar-text-hover);
}

.wms-aside :deep(.el-menu-item.is-active) {
  background: var(--wms-sidebar-active-bg);
  color: var(--wms-sidebar-active-text);
  font-weight: 500;
}

.wms-aside :deep(.el-menu-item.is-active .el-icon) {
  color: var(--wms-sidebar-active-text);
}

.wms-aside :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  padding-left: 48px !important;
  margin: 1px 10px;
}

.wms-aside :deep(.el-sub-menu__icon-arrow) {
  color: rgba(255, 255, 255, 0.45);
}

.wms-aside :deep(.el-icon) {
  color: inherit;
  font-size: 16px;
}

.wms-main-wrap {
  min-width: 0;
  background: var(--wms-page-bg);
}

.wms-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--wms-card-bg);
  border-bottom: 1px solid #ebeef5;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.02);
}

.wms-header :deep(.el-breadcrumb__inner) {
  color: var(--wms-text-secondary);
  font-weight: 400;
}

.wms-header :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--wms-text-primary);
  font-weight: 500;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background var(--wms-transition-fast);
}

.user-trigger:hover {
  background: #f5f7fa;
}

.user-avatar {
  background: linear-gradient(135deg, #d4a574, #b8895a);
  color: #fff;
}

.user-name {
  font-size: 14px;
  color: var(--wms-text-primary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-arrow {
  font-size: 12px;
  color: var(--wms-text-secondary);
}

.wms-main {
  padding: 20px 24px 24px;
  overflow: auto;
}

@media (max-width: 768px) {
  .wms-aside {
    position: fixed;
    z-index: 100;
    height: 100vh;
    transform: translateX(-100%);
  }

  .wms-main {
    padding: 16px;
  }

  .user-name {
    display: none;
  }
}
</style>
