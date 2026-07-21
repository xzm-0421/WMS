-- 生产领料单 PDA 联调种子：物料 / 仓库 / 库存
IF NOT EXISTS (SELECT 1 FROM base_warehouse WHERE warehouse_code = 'CK004' AND deleted = 0)
    INSERT INTO base_warehouse (warehouse_code, warehouse_name, warehouse_type, status, create_by, erp_warehouse_code)
    VALUES ('CK004', N'原材料仓', 'RAW', 1, 'system', 'CK004');
ELSE
    UPDATE base_warehouse
    SET deleted = 0, status = 1, erp_warehouse_code = ISNULL(NULLIF(LTRIM(RTRIM(erp_warehouse_code)), ''), 'CK004')
    WHERE warehouse_code = 'CK004';

IF NOT EXISTS (SELECT 1 FROM base_location WHERE location_code = 'CK004-A01' AND deleted = 0)
    INSERT INTO base_location (location_code, location_name, warehouse_code, zone_code, location_type, status)
    VALUES ('CK004-A01', N'A01货位', 'CK004', 'A', 'STORAGE', 1);

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'PITEST-001')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('PITEST-001', N'螺栓 M8', 'CAT001', N'M8×20', 'PCS', 'RAW', 'QR', 1, 1, 'system');
ELSE
    UPDATE base_material SET deleted = 0, status = 1, material_name = N'螺栓 M8', specification = N'M8×20'
    WHERE material_code = 'PITEST-001';

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'PITEST-002')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('PITEST-002', N'垫片 Φ8', 'CAT001', N'Φ8×1.5', 'PCS', 'RAW', 'QR', 1, 1, 'system');
ELSE
    UPDATE base_material SET deleted = 0, status = 1, material_name = N'垫片 Φ8', specification = N'Φ8×1.5'
    WHERE material_code = 'PITEST-002';

IF NOT EXISTS (
    SELECT 1 FROM inventory
    WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
      AND material_code = 'PITEST-001' AND batch_no = 'B20260715'
)
    INSERT INTO inventory (warehouse_code, location_code, material_code, batch_no, stock_qty, available_qty, frozen_qty, in_transit_qty, stock_status, inbound_date)
    VALUES ('CK004', 'CK004-A01', 'PITEST-001', 'B20260715', 500, 500, 0, 0, 'AVAILABLE', GETDATE());
ELSE
    UPDATE inventory
    SET stock_qty = 500, available_qty = 500, frozen_qty = 0, update_time = GETDATE()
    WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
      AND material_code = 'PITEST-001' AND batch_no = 'B20260715';

IF NOT EXISTS (
    SELECT 1 FROM inventory
    WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
      AND material_code = 'PITEST-002' AND batch_no = 'B20260715'
)
    INSERT INTO inventory (warehouse_code, location_code, material_code, batch_no, stock_qty, available_qty, frozen_qty, in_transit_qty, stock_status, inbound_date)
    VALUES ('CK004', 'CK004-A01', 'PITEST-002', 'B20260715', 300, 300, 0, 0, 'AVAILABLE', GETDATE());
ELSE
    UPDATE inventory
    SET stock_qty = 300, available_qty = 300, frozen_qty = 0, update_time = GETDATE()
    WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
      AND material_code = 'PITEST-002' AND batch_no = 'B20260715';
