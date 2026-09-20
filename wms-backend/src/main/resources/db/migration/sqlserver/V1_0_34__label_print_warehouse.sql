ALTER TABLE label_print_job ADD warehouse_code VARCHAR(50) NULL;
ALTER TABLE label_print_job ADD warehouse_name NVARCHAR(100) NULL;
CREATE INDEX idx_label_print_job_warehouse ON label_print_job(warehouse_code, create_time);
