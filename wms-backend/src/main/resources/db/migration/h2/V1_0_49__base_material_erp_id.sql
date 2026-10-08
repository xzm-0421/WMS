-- 物料内码改造 P1：base_material 增加金蝶内码 erp_material_id
-- 可空加列；重新同步回填后再由加固脚本收紧为 NOT NULL + 唯一索引 uk_material_erp_id
ALTER TABLE base_material ADD COLUMN IF NOT EXISTS erp_material_id BIGINT;

CREATE INDEX IF NOT EXISTS ix_base_material_erp_id ON base_material (erp_material_id);
