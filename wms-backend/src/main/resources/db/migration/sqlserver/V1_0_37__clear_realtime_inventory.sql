-- 清空实时库存与流水，后续以期初库存导入 + 金蝶库存同步重建
DELETE FROM inventory_transaction;
DELETE FROM inventory;
