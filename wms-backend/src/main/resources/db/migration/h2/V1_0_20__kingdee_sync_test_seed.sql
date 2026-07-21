-- 金蝶同步测试数据（收料通知单 CGSL240801721，仅测试用）
INSERT INTO pda_receive_submit_batch (
    batch_no, bill_type, direction, bill_no,
    supplier_code, supplier_name,
    line_count, total_qty,
    erp_sync_status, operator_id, operator_name, device_no, submit_time
)
SELECT 'RSB20260710100001', 'PURCHASE_RECEIVE', 'INBOUND', 'CGSL240801721',
       'G1803', '金蝶测试供应商',
       1, 61.0000,
       'PENDING', '1', '测试员', 'PDA-TEST', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM pda_receive_submit_batch WHERE batch_no = 'RSB20260710100001');

INSERT INTO pda_inbound_record (
    record_no, material_code, material_name, specification, unit_code,
    warehouse_code, location_code, batch_no, quantity,
    operator_id, operator_name, device_no,
    status, erp_sync_status, erp_retry_count,
    source_type, source_bill_no, source_line_no, submit_batch_no,
    supplier_code, erp_stock_code, remark,
    create_time, deleted
)
SELECT 'RIN20260710100001', 'SJ-HB01-002-29', '拆生螺杆2', NULL, 'Pcs',
       'WH01', 'WH01A1010101', '20260710', 61.0000,
       '1', '测试员', 'PDA-TEST',
       'SUBMITTED', 'PENDING', 0,
       'RECEIVE_NOTICE', 'CGSL240801721', 4, 'RSB20260710100001',
       'G1803', 'CK004', '金蝶同步测试数据-勿用于生产',
       CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM pda_inbound_record WHERE record_no = 'RIN20260710100001');
