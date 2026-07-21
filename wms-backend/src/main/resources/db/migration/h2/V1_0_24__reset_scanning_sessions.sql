-- 将「扫码中」会话重置为未扫码：删除仅处于 SCANNING 的会话及明细行
DELETE FROM pda_receive_scan_line
WHERE (bill_type, direction, bill_no) IN (
    SELECT bill_type, direction, bill_no FROM pda_receive_scan_session WHERE status = 'SCANNING'
);

DELETE FROM pda_receive_scan_session WHERE status = 'SCANNING';
