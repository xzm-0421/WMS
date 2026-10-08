-- 物料内码改造 P2：用本地 base_material 内码按编码回填 MES/BOM 表
-- 纯本地回填，不依赖金蝶；无匹配或内码为空时跳过。NOT NULL 收紧仍由加固脚本处理。

UPDATE mes_route t
SET erp_material_id = (
    SELECT b.erp_material_id FROM base_material b
    WHERE b.material_code = t.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC LIMIT 1)
WHERE t.erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = t.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);

UPDATE mes_route_op t
SET erp_material_id = (
    SELECT b.erp_material_id FROM base_material b
    WHERE b.material_code = t.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC LIMIT 1)
WHERE t.erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = t.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);

UPDATE mes_op_plan t
SET erp_material_id = (
    SELECT b.erp_material_id FROM base_material b
    WHERE b.material_code = t.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC LIMIT 1)
WHERE t.erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = t.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);

UPDATE bom_header t
SET erp_material_id = (
    SELECT b.erp_material_id FROM base_material b
    WHERE b.material_code = t.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC LIMIT 1)
WHERE t.erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = t.product_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);

UPDATE bom_detail t
SET erp_material_id = (
    SELECT b.erp_material_id FROM base_material b
    WHERE b.material_code = t.material_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL
    ORDER BY b.id DESC LIMIT 1)
WHERE t.erp_material_id IS NULL
  AND EXISTS (SELECT 1 FROM base_material b
              WHERE b.material_code = t.material_code AND b.deleted = 0 AND b.erp_material_id IS NOT NULL);
