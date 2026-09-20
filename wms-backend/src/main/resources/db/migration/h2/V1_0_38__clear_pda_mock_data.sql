-- 清理 PDA 模拟/联调残留（H2）
DELETE FROM pda_receive_scan_line
WHERE bill_no LIKE 'SLD%'
   OR bill_no IN (
        'SCL20260715001', 'SCB20260715001', 'SCT20260715001',
        'MORPT20260715001', 'STK20260715001',
        'WWL20260715001', 'WWB20260715001', 'WWT20260715001',
        'SLD20260708001', 'CGSL240801721'
   )
   OR material_code IN (
        'MAT-10001', 'MAT-10002', 'MAT-10003',
        'MAT-20001', 'MAT-20002',
        'MAT-30001', 'MAT-30002', 'MAT-30003', 'MAT-30004',
        'MAT-001', 'MAT-002', 'MAT-003', 'MAT-010',
        'PITEST-001', 'PITEST-002', 'FG-TEST-001', 'FG-TEST-002'
   );

DELETE FROM pda_receive_scan_session
WHERE bill_no LIKE 'SLD%'
   OR bill_no IN (
        'SCL20260715001', 'SCB20260715001', 'SCT20260715001',
        'MORPT20260715001', 'STK20260715001',
        'WWL20260715001', 'WWB20260715001', 'WWT20260715001',
        'SLD20260708001', 'CGSL240801721'
   );

DELETE FROM pda_inbound_record
WHERE source_bill_no LIKE 'SLD%'
   OR device_no IN ('PDA-TEST', 'PDA-SN-DEV001')
   OR record_no = 'RIN20260710100001'
   OR material_code IN (
        'MAT-10001', 'MAT-10002', 'MAT-10003',
        'MAT-20001', 'MAT-20002',
        'MAT-30001', 'MAT-30002', 'MAT-30003', 'MAT-30004',
        'MAT-001', 'MAT-002', 'MAT-003', 'MAT-010',
        'PITEST-001', 'PITEST-002', 'FG-TEST-001', 'FG-TEST-002', 'MAT00000123'
   );

DELETE FROM pda_receive_submit_batch
WHERE bill_no LIKE 'SLD%'
   OR batch_no = 'RSB20260710100001'
   OR device_no IN ('PDA-TEST', 'PDA-SN-DEV001');

DELETE FROM pda_stockcount_line
WHERE bill_no IN ('PD202507210001', 'PD202507200002')
   OR material_code IN ('MAT-001', 'MAT-002', 'MAT-003', 'MAT-010');

DELETE FROM pda_stockcount_session
WHERE bill_no IN ('PD202507210001', 'PD202507200002');

DELETE FROM pda_bill_lock
WHERE bill_no LIKE 'SLD%'
   OR bill_no IN (
        'SCL20260715001', 'SCB20260715001', 'SCT20260715001',
        'PD202507210001', 'PD202507200002'
   );

DELETE FROM label_print_job
WHERE source_bill_no LIKE 'SLD%'
   OR material_code IN (
        'MAT-10001', 'MAT-10002', 'MAT-10003',
        'MAT-20001', 'MAT-20002',
        'MAT-30001', 'MAT-30002', 'MAT-30003', 'MAT-30004',
        'PITEST-001', 'PITEST-002'
   );

DELETE FROM barcode_trace_link
WHERE material_code IN (
        'MAT-10001', 'MAT-10002', 'MAT-10003',
        'MAT-20001', 'MAT-20002',
        'MAT-30001', 'MAT-30002', 'MAT-30003', 'MAT-30004',
        'PITEST-001', 'PITEST-002'
   );

DELETE FROM barcode_instance
WHERE material_code IN (
        'MAT-10001', 'MAT-10002', 'MAT-10003',
        'MAT-20001', 'MAT-20002',
        'MAT-30001', 'MAT-30002', 'MAT-30003', 'MAT-30004',
        'PITEST-001', 'PITEST-002'
   );

DELETE FROM inventory
WHERE material_code IN (
        'MAT-10001', 'MAT-10002', 'MAT-10003',
        'MAT-20001', 'MAT-20002',
        'MAT-30001', 'MAT-30002', 'MAT-30003', 'MAT-30004',
        'MAT-001', 'MAT-002', 'MAT-003', 'MAT-010',
        'PITEST-001', 'PITEST-002', 'FG-TEST-001', 'FG-TEST-002', 'MAT00000123'
   );

DELETE FROM inventory_transaction
WHERE material_code IN (
        'MAT-10001', 'MAT-10002', 'MAT-10003',
        'MAT-20001', 'MAT-20002',
        'MAT-30001', 'MAT-30002', 'MAT-30003', 'MAT-30004',
        'MAT-001', 'MAT-002', 'MAT-003', 'MAT-010',
        'PITEST-001', 'PITEST-002', 'FG-TEST-001', 'FG-TEST-002', 'MAT00000123'
   );

UPDATE base_material
SET deleted = 1, update_time = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND material_code IN (
        'MAT-10001', 'MAT-10002', 'MAT-10003',
        'MAT-20001', 'MAT-20002',
        'MAT-30001', 'MAT-30002', 'MAT-30003', 'MAT-30004',
        'MAT-001', 'MAT-002', 'MAT-003', 'MAT-010',
        'PITEST-001', 'PITEST-002', 'FG-TEST-001', 'FG-TEST-002', 'MAT00000123'
  );
