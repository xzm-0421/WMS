-- 删除 WH01 仓库及其库存（改用金蝶 CK004 等仓库编码）
DELETE FROM inventory WHERE warehouse_code = 'WH01';
DELETE FROM inventory_transaction WHERE warehouse_code = 'WH01';

UPDATE base_location
SET deleted = 1, update_time = CURRENT_TIMESTAMP
WHERE warehouse_code = 'WH01' AND deleted = 0;

UPDATE base_warehouse_zone
SET deleted = 1
WHERE warehouse_code = 'WH01' AND deleted = 0;

UPDATE base_warehouse
SET deleted = 1, status = 0, update_time = CURRENT_TIMESTAMP
WHERE warehouse_code = 'WH01' AND deleted = 0;
