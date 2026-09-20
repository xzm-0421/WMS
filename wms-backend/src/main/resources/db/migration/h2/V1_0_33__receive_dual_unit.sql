-- 收料通知单多单位：库存单位 + 计价/辅助单位
ALTER TABLE pda_receive_scan_line ADD COLUMN IF NOT EXISTS aux_unit_code VARCHAR(20);
ALTER TABLE pda_receive_scan_line ADD COLUMN IF NOT EXISTS plan_aux_qty DECIMAL(18, 6);
ALTER TABLE pda_receive_scan_line ADD COLUMN IF NOT EXISTS scanned_aux_qty DECIMAL(18, 6);
ALTER TABLE pda_receive_scan_line ADD COLUMN IF NOT EXISTS submitted_aux_qty DECIMAL(18, 6);

ALTER TABLE pda_inbound_record ADD COLUMN IF NOT EXISTS aux_unit_code VARCHAR(20);
ALTER TABLE pda_inbound_record ADD COLUMN IF NOT EXISTS aux_quantity DECIMAL(18, 6);
