-- 物料内码改造 P1：base_material 增加金蝶内码 erp_material_id
-- 可空加列；重新同步回填后再由加固脚本收紧为 NOT NULL + 唯一索引 uk_material_erp_id
IF COL_LENGTH('base_material', 'erp_material_id') IS NULL
    ALTER TABLE base_material ADD erp_material_id BIGINT;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_base_material_erp_id' AND object_id = OBJECT_ID('base_material'))
    CREATE INDEX ix_base_material_erp_id ON base_material (erp_material_id);
