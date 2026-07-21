-- 将「扫码中」会话重置为未扫码：删除仅处于 SCANNING 的会话及明细行
DELETE l
FROM pda_receive_scan_line l
INNER JOIN pda_receive_scan_session s
    ON l.bill_type = s.bill_type
   AND l.direction = s.direction
   AND l.bill_no = s.bill_no
WHERE s.status = 'SCANNING';

DELETE FROM pda_receive_scan_session WHERE status = 'SCANNING';
