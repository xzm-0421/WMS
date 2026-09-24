export const DATA_SCOPE_LABELS: Record<number, string> = {

  1: '全部数据',

  2: '指定仓库',

  3: '本部门',

  4: '本部门及以下',

}



export const MENU_PERMISSIONS: Record<string, string> = {

  '/dashboard': 'dashboard:view',

  '/system/users': 'system:user:list',

  '/system/roles': 'system:role:list',

  '/base/materials': 'base:material:list',

  '/base/warehouses': 'base:warehouse:list',

  '/base/locations': 'base:location:list',

  '/inbound/orders': 'inbound:list',

  '/inbound/pda-records': 'inbound:record:list',

  '/outbound/orders': 'outbound:list',

  '/outbound/pda-records': 'outbound:record:list',

  '/inventory/list': 'inventory:list',

  '/inventory/sample-plans': 'inventory:sample:list',

  '/inventory/transfers': 'inventory:transfer:list',

  '/stocktake/plans': 'stocktake:list',

  '/stocktake/tasks': 'stocktake:list',

  '/stocktake/diffs': 'stocktake:list',

  '/quality/standards': 'qc:list',

  '/quality/orders': 'qc:list',

  '/barcode/rules': 'barcode:list',

  '/print/label-jobs': 'print:label:list',

  '/dashboard/warehouse': 'dashboard:warehouse',

  '/system/rules': 'system:rule:list',

  '/system/business-flow': 'system:flow:view',

  '/report/overview': 'report:view',

  '/mes/materials': 'mes:material:list',
  '/mes/boms': 'mes:bom:list',
  '/mes/process': 'mes:process:list',
  '/mes/equipment': 'mes:equipment:list',
  '/mes/routes': 'mes:route:list',
  '/mes/plans': 'mes:plan:list',
  '/mes/report': 'mes:report:submit',
  '/mes/transfer': 'mes:transfer:submit',
  '/mes/rework': 'mes:rework:view',
  '/mes/reports': 'mes:report:list',
  '/mes/sync': 'mes:sync:panel',

}



export function hasPermission(

  permissions: string[] | undefined,

  roles: string[] | undefined,

  code?: string

) {

  if (!code) return true

  if (roles?.includes('SUPER_ADMIN')) return true

  return permissions?.includes(code) ?? false

}



export function canAccessPath(

  path: string,

  permissions: string[] | undefined,

  roles: string[] | undefined

) {

  const code = MENU_PERMISSIONS[path]

  if (hasPermission(permissions, roles, code)) {
    return true
  }
  if (path === '/mes/process' || path === '/mes/equipment' || path === '/mes/routes'
    || path === '/mes/materials' || path === '/mes/boms') {
    return hasPermission(permissions, roles, 'mes:master:list')
  }
  return false

}

