-- 通知单扫码统一：按单据类型 + 方向区分会话
ALTER TABLE pda_receive_scan_session ADD bill_type VARCHAR(32) NOT NULL DEFAULT 'PURCHASE_RECEIVE';
ALTER TABLE pda_receive_scan_session ADD direction VARCHAR(16) NOT NULL DEFAULT 'INBOUND';

ALTER TABLE pda_receive_scan_session DROP CONSTRAINT uk_pda_receive_session_bill;
ALTER TABLE pda_receive_scan_session ADD CONSTRAINT uk_pda_notice_session UNIQUE (bill_type, direction, bill_no);

ALTER TABLE pda_receive_scan_line ADD bill_type VARCHAR(32) NOT NULL DEFAULT 'PURCHASE_RECEIVE';
ALTER TABLE pda_receive_scan_line ADD direction VARCHAR(16) NOT NULL DEFAULT 'INBOUND';

ALTER TABLE pda_receive_scan_line DROP CONSTRAINT uk_pda_receive_line;
ALTER TABLE pda_receive_scan_line ADD CONSTRAINT uk_pda_notice_line UNIQUE (bill_type, direction, bill_no, line_no);

ALTER TABLE pda_receive_submit_batch ADD bill_type VARCHAR(32) NOT NULL DEFAULT 'PURCHASE_RECEIVE';
ALTER TABLE pda_receive_submit_batch ADD direction VARCHAR(16) NOT NULL DEFAULT 'INBOUND';

CREATE INDEX idx_pda_notice_session_type ON pda_receive_scan_session(bill_type, direction, status, update_time);
