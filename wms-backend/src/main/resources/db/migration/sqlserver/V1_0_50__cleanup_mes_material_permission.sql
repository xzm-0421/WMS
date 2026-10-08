-- 物料内码改造 P1.4：MES 物料菜单合并到基础数据物料页，清理旧权限码
-- 角色绑定先删除，再软删权限行；新增的 base:material:sync 由 PermissionInitializer 写入
DELETE FROM sys_role_permission
WHERE permission_id IN (
    SELECT id FROM sys_permission WHERE permission_code IN ('mes:material:list', 'mes:material:sync')
);

UPDATE sys_permission SET deleted = 1
WHERE permission_code IN ('mes:material:list', 'mes:material:sync');
