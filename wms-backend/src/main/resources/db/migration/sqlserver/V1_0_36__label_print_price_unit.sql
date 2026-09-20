-- 期初库存标签：计价单位
IF COL_LENGTH('label_print_job', 'price_unit_code') IS NULL
BEGIN
    ALTER TABLE label_print_job ADD price_unit_code NVARCHAR(50) NULL;
END
GO
