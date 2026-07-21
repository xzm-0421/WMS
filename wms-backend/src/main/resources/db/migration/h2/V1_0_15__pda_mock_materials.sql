-- PDA 收料 mock 物料主数据（与 KingdeeReceiveBillService mock 一致）
INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'MAT-10001', '电阻 10K', 'CAT001', '10KΩ ±1%', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-10001');

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'MAT-10002', '电容 100uF', 'CAT001', '100uF/25V', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-10002');

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'MAT-10003', 'PCB主板', 'CAT001', 'V2.1-A', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-10003');

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'MAT-20001', '连接器', 'CAT001', 'Type-C', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-20001');

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'MAT-20002', '屏蔽罩', 'CAT001', 'SUS304', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-20002');

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'MAT-30001', '螺丝 M3', 'CAT001', 'M3*8', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-30001');

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'MAT-30002', '垫片', 'CAT001', 'φ3', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-30002');

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'MAT-30003', '包装盒', 'CAT001', '200*150', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-30003');

INSERT INTO base_material (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
SELECT 'MAT-30004', '标签纸', 'CAT001', '100*60', 'PCS', 'RAW', 'QR', 1, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM base_material WHERE material_code = 'MAT-30004');
