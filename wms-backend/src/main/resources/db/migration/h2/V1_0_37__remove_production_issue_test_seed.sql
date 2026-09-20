-- 清除生产领料联调种子（CK004 测试仓 / PITEST 物料与库存）
DELETE FROM inventory_transaction WHERE material_code IN ('PITEST-001', 'PITEST-002');
DELETE FROM inventory WHERE material_code IN ('PITEST-001', 'PITEST-002');
DELETE FROM inventory
WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01' AND batch_no = 'B20260715';

DELETE FROM base_location WHERE location_code = 'CK004-A01' AND warehouse_code = 'CK004';

DELETE FROM base_warehouse
WHERE warehouse_code = 'CK004'
  AND create_by = 'system'
  AND warehouse_name = '原材料仓';

DELETE FROM base_material
WHERE material_code IN ('PITEST-001', 'PITEST-002')
  AND create_by = 'system';
