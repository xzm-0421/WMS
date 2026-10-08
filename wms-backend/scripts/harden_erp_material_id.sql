-- ============================================================================
-- 物料内码加固脚本（手动执行，非 Flyway 自动迁移）
-- 适用：SQL Server（生产/开发）
-- 时机：在全量重新同步（金蝶 BD_MATERIAL / ENG_BOM / ENG_ROUTE / PRD_PROCESSCHEDULE）
--       回填内码之后执行。
-- 前置校验：以下计数必须为 0（未删除行内码为空则不可收紧）：
--   SELECT COUNT(*) FROM base_material WHERE deleted = 0 AND erp_material_id IS NULL;
--   SELECT COUNT(*) FROM mes_route     WHERE deleted = 0 AND erp_material_id IS NULL;
--   SELECT COUNT(*) FROM mes_route_op  WHERE deleted = 0 AND erp_material_id IS NULL;
--   SELECT COUNT(*) FROM mes_op_plan   WHERE deleted = 0 AND erp_material_id IS NULL;
--   SELECT COUNT(*) FROM bom_header    WHERE erp_material_id IS NULL;
--   SELECT COUNT(*) FROM bom_detail    WHERE erp_material_id IS NULL;
-- ============================================================================

-- base_material：未删除行内码非空 + 按内码唯一（过滤逻辑删除行）
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_base_material_erp_id')
    ALTER TABLE base_material ADD CONSTRAINT ck_base_material_erp_id
        CHECK (deleted = 1 OR erp_material_id IS NOT NULL);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_material_erp_id' AND object_id = OBJECT_ID('base_material'))
    CREATE UNIQUE INDEX uk_material_erp_id ON base_material (erp_material_id) WHERE deleted = 0;

-- MES 逻辑删除表：未删除行内码非空
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_mes_route_erp_id')
    ALTER TABLE mes_route ADD CONSTRAINT ck_mes_route_erp_id
        CHECK (deleted = 1 OR erp_material_id IS NOT NULL);

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_mes_route_op_erp_id')
    ALTER TABLE mes_route_op ADD CONSTRAINT ck_mes_route_op_erp_id
        CHECK (deleted = 1 OR erp_material_id IS NOT NULL);

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_mes_op_plan_erp_id')
    ALTER TABLE mes_op_plan ADD CONSTRAINT ck_mes_op_plan_erp_id
        CHECK (deleted = 1 OR erp_material_id IS NOT NULL);

-- BOM 表无逻辑删除列，直接收紧为 NOT NULL
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('bom_header') AND name = 'erp_material_id' AND is_nullable = 1)
    ALTER TABLE bom_header ALTER COLUMN erp_material_id BIGINT NOT NULL;

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('bom_detail') AND name = 'erp_material_id' AND is_nullable = 1)
    ALTER TABLE bom_detail ALTER COLUMN erp_material_id BIGINT NOT NULL;
