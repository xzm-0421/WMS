-- 收料通知单多单位：库存单位 + 计价/辅助单位
ALTER TABLE pda_receive_scan_line ADD aux_unit_code VARCHAR(20) NULL;
ALTER TABLE pda_receive_scan_line ADD plan_aux_qty DECIMAL(18, 6) NULL;
ALTER TABLE pda_receive_scan_line ADD scanned_aux_qty DECIMAL(18, 6) NULL;
ALTER TABLE pda_receive_scan_line ADD submitted_aux_qty DECIMAL(18, 6) NULL;

ALTER TABLE pda_inbound_record ADD aux_unit_code VARCHAR(20) NULL;
ALTER TABLE pda_inbound_record ADD aux_quantity DECIMAL(18, 6) NULL;
