-- 收料批次同步金蝶：持久化供应商与金蝶仓库
ALTER TABLE pda_receive_submit_batch ADD supplier_code VARCHAR(64);
ALTER TABLE pda_receive_submit_batch ADD supplier_name VARCHAR(200);

ALTER TABLE pda_receive_scan_line ADD erp_stock_code VARCHAR(50);

ALTER TABLE pda_inbound_record ADD supplier_code VARCHAR(64);
ALTER TABLE pda_inbound_record ADD erp_stock_code VARCHAR(50);
