ALTER TABLE pda_inbound_record ADD COLUMN IF NOT EXISTS source_remain_in_stock_base_qty_old DECIMAL(18,4);
ALTER TABLE pda_inbound_record ADD COLUMN IF NOT EXISTS source_base_unit_qty_old DECIMAL(18,4);
