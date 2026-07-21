-- WMS 仓库与金蝶仓库（FStockId）对照
ALTER TABLE base_warehouse ADD COLUMN IF NOT EXISTS erp_warehouse_code VARCHAR(50);

UPDATE base_warehouse SET erp_warehouse_code = 'CK004' WHERE warehouse_code = 'WH01';
