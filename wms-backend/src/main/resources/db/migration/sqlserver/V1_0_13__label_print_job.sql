-- 金蝶云星空物料标签打印任务日志
CREATE TABLE label_print_job (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id              VARCHAR(32) NOT NULL,
    source_type         VARCHAR(30) NOT NULL DEFAULT 'KINGDEE',
    source_bill_no      VARCHAR(64),
    material_code       VARCHAR(64) NOT NULL,
    material_name       VARCHAR(200),
    specification       VARCHAR(200),
    batch_no            VARCHAR(64),
    quantity            DECIMAL(18,4),
    unit_code           VARCHAR(20),
    barcode_content     VARCHAR(200) NOT NULL,
    barcode_type        VARCHAR(20) NOT NULL DEFAULT 'QR',
    label_width_mm      DECIMAL(8,2) DEFAULT 100,
    label_height_mm     DECIMAL(8,2) DEFAULT 60,
    copies              INT NOT NULL DEFAULT 1,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    operator_id         VARCHAR(50),
    operator_name       VARCHAR(50),
    request_payload     NVARCHAR(MAX),
    error_message       NVARCHAR(500),
    create_time         DATETIME2 NOT NULL DEFAULT GETDATE(),
    opened_time         DATETIME2,
    printed_time        DATETIME2,
    CONSTRAINT uk_label_print_job_id UNIQUE (job_id)
);

CREATE INDEX idx_label_print_job_status ON label_print_job(status, create_time);
CREATE INDEX idx_label_print_job_material ON label_print_job(material_code, create_time);
CREATE INDEX idx_label_print_job_source ON label_print_job(source_bill_no);
