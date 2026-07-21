-- PDA 收料 mock 物料主数据（与 KingdeeReceiveBillService mock 一致）
IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-10001')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('MAT-10001', N'电阻 10K', 'CAT001', N'10KΩ ±1%', 'PCS', 'RAW', 'QR', 1, 1, 'system');

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-10002')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('MAT-10002', N'电容 100uF', 'CAT001', N'100uF/25V', 'PCS', 'RAW', 'QR', 1, 1, 'system');

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-10003')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('MAT-10003', N'PCB主板', 'CAT001', N'V2.1-A', 'PCS', 'RAW', 'QR', 1, 1, 'system');

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-20001')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('MAT-20001', N'连接器', 'CAT001', N'Type-C', 'PCS', 'RAW', 'QR', 1, 1, 'system');

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-20002')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('MAT-20002', N'屏蔽罩', 'CAT001', N'SUS304', 'PCS', 'RAW', 'QR', 1, 1, 'system');

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-30001')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('MAT-30001', N'螺丝 M3', 'CAT001', N'M3*8', 'PCS', 'RAW', 'QR', 1, 1, 'system');

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-30002')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('MAT-30002', N'垫片', 'CAT001', N'φ3', 'PCS', 'RAW', 'QR', 1, 1, 'system');

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-30003')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('MAT-30003', N'包装盒', 'CAT001', N'200*150', 'PCS', 'RAW', 'QR', 1, 1, 'system');

IF NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-30004')
    INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
    VALUES ('MAT-30004', N'标签纸', 'CAT001', N'100*60', 'PCS', 'RAW', 'QR', 1, 1, 'system');
