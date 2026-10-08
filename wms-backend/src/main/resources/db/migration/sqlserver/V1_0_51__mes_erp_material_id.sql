-- 物料内码改造 P2：MES/BOM 表增加金蝶内码 erp_material_id
-- 可空加列；重新同步回填后再由加固脚本收紧为 NOT NULL
IF COL_LENGTH('bom_header', 'erp_material_id') IS NULL
    ALTER TABLE bom_header ADD erp_material_id BIGINT;
IF COL_LENGTH('bom_detail', 'erp_material_id') IS NULL
    ALTER TABLE bom_detail ADD erp_material_id BIGINT;
IF COL_LENGTH('mes_route', 'erp_material_id') IS NULL
    ALTER TABLE mes_route ADD erp_material_id BIGINT;
IF COL_LENGTH('mes_route_op', 'erp_material_id') IS NULL
    ALTER TABLE mes_route_op ADD erp_material_id BIGINT;
IF COL_LENGTH('mes_op_plan', 'erp_material_id') IS NULL
    ALTER TABLE mes_op_plan ADD erp_material_id BIGINT;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bom_header_erp_material_id' AND object_id = OBJECT_ID('bom_header'))
    CREATE INDEX ix_bom_header_erp_material_id ON bom_header (erp_material_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bom_detail_erp_material_id' AND object_id = OBJECT_ID('bom_detail'))
    CREATE INDEX ix_bom_detail_erp_material_id ON bom_detail (erp_material_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_mes_route_erp_material_id' AND object_id = OBJECT_ID('mes_route'))
    CREATE INDEX ix_mes_route_erp_material_id ON mes_route (erp_material_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_mes_route_op_erp_material_id' AND object_id = OBJECT_ID('mes_route_op'))
    CREATE INDEX ix_mes_route_op_erp_material_id ON mes_route_op (erp_material_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_mes_op_plan_erp_material_id' AND object_id = OBJECT_ID('mes_op_plan'))
    CREATE INDEX ix_mes_op_plan_erp_material_id ON mes_op_plan (erp_material_id);
