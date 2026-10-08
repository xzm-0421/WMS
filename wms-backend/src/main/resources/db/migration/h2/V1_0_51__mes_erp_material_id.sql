-- 物料内码改造 P2：MES/BOM 表增加金蝶内码 erp_material_id
-- 可空加列；重新同步回填后再由加固脚本收紧为 NOT NULL
ALTER TABLE bom_header ADD COLUMN IF NOT EXISTS erp_material_id BIGINT;
ALTER TABLE bom_detail ADD COLUMN IF NOT EXISTS erp_material_id BIGINT;
ALTER TABLE mes_route ADD COLUMN IF NOT EXISTS erp_material_id BIGINT;
ALTER TABLE mes_route_op ADD COLUMN IF NOT EXISTS erp_material_id BIGINT;
ALTER TABLE mes_op_plan ADD COLUMN IF NOT EXISTS erp_material_id BIGINT;

CREATE INDEX IF NOT EXISTS ix_bom_header_erp_material_id ON bom_header (erp_material_id);
CREATE INDEX IF NOT EXISTS ix_bom_detail_erp_material_id ON bom_detail (erp_material_id);
CREATE INDEX IF NOT EXISTS ix_mes_route_erp_material_id ON mes_route (erp_material_id);
CREATE INDEX IF NOT EXISTS ix_mes_route_op_erp_material_id ON mes_route_op (erp_material_id);
CREATE INDEX IF NOT EXISTS ix_mes_op_plan_erp_material_id ON mes_op_plan (erp_material_id);
