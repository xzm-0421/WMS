-- WMS 仓库与金蝶仓库（FStockId）对照
IF COL_LENGTH('base_warehouse', 'erp_warehouse_code') IS NULL
    ALTER TABLE base_warehouse ADD erp_warehouse_code VARCHAR(50);
