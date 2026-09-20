IF COL_LENGTH('label_print_job', 'label_format') IS NULL
BEGIN
    ALTER TABLE label_print_job ADD label_format VARCHAR(20) NULL;
END;
IF COL_LENGTH('label_print_job', 'partner_name') IS NULL
BEGIN
    ALTER TABLE label_print_job ADD partner_name NVARCHAR(100) NULL;
END;
IF COL_LENGTH('label_print_job', 'board_no') IS NULL
BEGIN
    ALTER TABLE label_print_job ADD board_no NVARCHAR(64) NULL;
END;
IF COL_LENGTH('label_print_job', 'package_no') IS NULL
BEGIN
    ALTER TABLE label_print_job ADD package_no NVARCHAR(64) NULL;
END;
