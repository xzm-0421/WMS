-- 默认 WMS 仓库与金蝶仓库映射（单独脚本，避免 SQL Server 同批次编译问题）
UPDATE base_warehouse
SET erp_warehouse_code = 'CK004'
WHERE warehouse_code = 'WH01'
  AND (erp_warehouse_code IS NULL OR LTRIM(RTRIM(erp_warehouse_code)) = '');
