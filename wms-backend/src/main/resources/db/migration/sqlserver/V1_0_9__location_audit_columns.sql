-- V1.0.9 补齐 base_location 审计字段（与 BaseEntity 对齐，修复库位查询 500）
IF COL_LENGTH('base_location', 'update_by') IS NULL
    ALTER TABLE base_location ADD update_by VARCHAR(50);
IF COL_LENGTH('base_location', 'update_time') IS NULL
    ALTER TABLE base_location ADD update_time DATETIME2;
