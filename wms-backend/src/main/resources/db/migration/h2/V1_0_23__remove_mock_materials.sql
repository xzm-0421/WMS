-- 清理演示/模拟物料及关联库存
DELETE FROM inventory
WHERE material_code LIKE 'MAT-%'
   OR material_code LIKE 'MAT00000%';

UPDATE base_material
SET deleted = 1, update_time = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND (
    material_code LIKE 'MAT-%'
    OR material_code LIKE 'MAT00000%'
    OR (category_code = 'CAT001' AND create_by = 'system')
  );
