-- 物料内码改造 P2：用本地 base_material 内码按编码回填 MES/BOM 表
-- 纯本地回填，不依赖金蝶；无匹配或内码为空时跳过。NOT NULL 收紧仍由加固脚本处理。

UPDATE mes_route
SET erp_material_id = (
    SELECT TOP 1 b.erp_material_id FROM base_material b
    WHERE b.material_code = mes_route.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC)
WHERE erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = mes_route.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);

UPDATE mes_route_op
SET erp_material_id = (
    SELECT TOP 1 b.erp_material_id FROM base_material b
    WHERE b.material_code = mes_route_op.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC)
WHERE erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = mes_route_op.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);

UPDATE mes_op_plan
SET erp_material_id = (
    SELECT TOP 1 b.erp_material_id FROM base_material b
    WHERE b.material_code = mes_op_plan.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC)
WHERE erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = mes_op_plan.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);

UPDATE bom_header
SET erp_material_id = (
    SELECT TOP 1 b.erp_material_id FROM base_material b
    WHERE b.material_code = bom_header.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC)
WHERE erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = bom_header.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);

UPDATE bom_detail
SET erp_material_id = (
    SELECT TOP 1 b.erp_material_id FROM base_material b
    WHERE b.material_code = bom_detail.material_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC)
WHERE erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = bom_detail.material_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);
