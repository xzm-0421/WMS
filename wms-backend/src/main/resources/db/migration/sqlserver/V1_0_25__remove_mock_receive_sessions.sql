-- 清理本地 Mock 收料单（SLD 前缀）遗留的扫码会话
DELETE l
FROM pda_receive_scan_line l
INNER JOIN pda_receive_scan_session s
    ON l.bill_type = s.bill_type
   AND l.direction = s.direction
   AND l.bill_no = s.bill_no
WHERE s.bill_no LIKE 'SLD%';

DELETE FROM pda_receive_scan_session WHERE bill_no LIKE 'SLD%';
