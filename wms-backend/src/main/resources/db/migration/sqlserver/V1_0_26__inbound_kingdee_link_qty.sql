-- 收料提交时持久化金蝶源单关联携带量，供采购入库单 FInStockEntry_Link 使用
ALTER TABLE pda_inbound_record ADD source_remain_in_stock_base_qty_old DECIMAL(18,4);
ALTER TABLE pda_inbound_record ADD source_base_unit_qty_old DECIMAL(18,4);
