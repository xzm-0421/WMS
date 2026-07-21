DELETE FROM pda_receive_scan_line
WHERE bill_no LIKE 'SLD%';

DELETE FROM pda_receive_scan_session WHERE bill_no LIKE 'SLD%';
