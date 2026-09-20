-- 轻 MES：按金蝶 OpenAPI 补齐工艺路线编码/名称、工序计划车间

ALTER TABLE mes_route ADD route_code VARCHAR(50) NULL;
ALTER TABLE mes_route ADD route_name VARCHAR(100) NULL;

ALTER TABLE mes_op_plan ADD work_shop_code VARCHAR(50) NULL;
ALTER TABLE mes_op_plan ADD work_shop_name VARCHAR(100) NULL;
