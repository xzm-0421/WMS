-- 收料提交时持久化金蝶源单内码，供同步时直接关联
ALTER TABLE pda_inbound_record ADD source_bill_id BIGINT;
ALTER TABLE pda_inbound_record ADD source_entry_id BIGINT;
