-- 生产领料单 PDA 联调种子：物料 / 仓库 / 库存
INSERT INTO base_warehouse (warehouse_code, warehouse_name, warehouse_type, status, create_by, erp_warehouse_code)
SELECT 'CK004', '原材料仓', 'RAW', 1, 'system', 'CK004'
WHERE NOT EXISTS (SELECT 1 FROM base_warehouse WHERE warehouse_code = 'CK004' AND deleted = 0);

UPDATE base_warehouse
SET deleted = 0, status = 1, erp_warehouse_code = COALESCE(NULLIF(TRIM(erp_warehouse_code), ''), 'CK004')
WHERE warehouse_code = 'CK004';

INSERT INTO base_location (location_code, location_name, warehouse_code, zone_code, location_type, status)
SELECT 'CK004-A01', 'A01货位', 'CK004', 'A', 'STORAGE', 1
WHERE NOT EXISTS (SELECT 1 FROM base_location WHERE location_code = 'CK004-A01' AND deleted = 0);

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'PITEST-001', '螺栓 M8', 'CAT001', 'M8×20', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'PITEST-001');

UPDATE base_material SET deleted = 0, status = 1 WHERE material_code = 'PITEST-001';

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'PITEST-002', '垫片 Φ8', 'CAT001', 'Φ8×1.5', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'PITEST-002');

UPDATE base_material SET deleted = 0, status = 1 WHERE material_code = 'PITEST-002';

INSERT INTO inventory (warehouse_code, location_code, material_code, batch_no, stock_qty, available_qty, frozen_qty, in_transit_qty, stock_status, inbound_date)
SELECT 'CK004', 'CK004-A01', 'PITEST-001', 'B20260715', 500, 500, 0, 0, 'AVAILABLE', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM inventory
    WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
      AND material_code = 'PITEST-001' AND batch_no = 'B20260715'
);

UPDATE inventory
SET stock_qty = 500, available_qty = 500, frozen_qty = 0, update_time = CURRENT_TIMESTAMP
WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
  AND material_code = 'PITEST-001' AND batch_no = 'B20260715';

INSERT INTO inventory (warehouse_code, location_code, material_code, batch_no, stock_qty, available_qty, frozen_qty, in_transit_qty, stock_status, inbound_date)
SELECT 'CK004', 'CK004-A01', 'PITEST-002', 'B20260715', 300, 300, 0, 0, 'AVAILABLE', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM inventory
    WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
      AND material_code = 'PITEST-002' AND batch_no = 'B20260715'
);

UPDATE inventory
SET stock_qty = 300, available_qty = 300, frozen_qty = 0, update_time = CURRENT_TIMESTAMP
WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
  AND material_code = 'PITEST-002' AND batch_no = 'B20260715';
