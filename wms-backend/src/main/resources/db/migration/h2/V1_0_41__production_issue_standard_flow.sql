-- 生产领料改为标准流程：取消「备货完成」中间态，历史 PREPARED 会话回到扫码中
UPDATE pda_receive_scan_session SET status = 'SCANNING' WHERE status = 'PREPARED';
