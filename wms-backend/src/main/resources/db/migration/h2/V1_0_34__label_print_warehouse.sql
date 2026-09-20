ALTER TABLE label_print_job ADD COLUMN warehouse_code VARCHAR(50);
ALTER TABLE label_print_job ADD COLUMN warehouse_name VARCHAR(100);
CREATE INDEX idx_label_print_job_warehouse ON label_print_job(warehouse_code, create_time);
