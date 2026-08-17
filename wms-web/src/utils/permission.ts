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

  '/outbound/pda-records': 'outbound:record:list',

  '/inventory/list': 'inventory:list',

  '/inventory/sample-plans': 'inventory:sample:list',

  '/barcode/rules': 'barcode:list',

  '/print/label-jobs': 'print:label:list',

  '/dashboard/warehouse': 'dashboard:warehouse',

  '/system/rules': 'system:rule:list',

  '/system/business-flow': 'system:flow:view',

  '/report/overview': 'report:view',

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

  return hasPermission(permissions, roles, code)

}

