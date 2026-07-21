-- V1.0.5 补齐基础表审计字段
ALTER TABLE base_warehouse ADD update_by VARCHAR(50);
ALTER TABLE base_warehouse ADD update_time TIMESTAMP;

ALTER TABLE base_supplier ADD update_by VARCHAR(50);
ALTER TABLE base_supplier ADD update_time TIMESTAMP;

ALTER TABLE base_customer ADD update_by VARCHAR(50);
ALTER TABLE base_customer ADD update_time TIMESTAMP;
