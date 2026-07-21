-- PDA 无单入库记录 & 金蝶同步日志
CREATE TABLE pda_inbound_record (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    record_no           VARCHAR(30) NOT NULL,
    material_code       VARCHAR(50) NOT NULL,
    material_name       VARCHAR(200),
    specification       VARCHAR(200),
    unit_code           VARCHAR(20),
    warehouse_code      VARCHAR(50) NOT NULL,
    location_code       VARCHAR(50) NOT NULL,
    batch_no            VARCHAR(50),
    quantity            DECIMAL(18,4) NOT NULL,
    barcode_content     VARCHAR(200),
    operator_id         VARCHAR(50),
    operator_name       VARCHAR(50),
    device_no           VARCHAR(50),
    status              VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    erp_sync_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    erp_sync_time       DATETIME2,
    erp_bill_no         VARCHAR(50),
    erp_sync_message    NVARCHAR(500),
    erp_retry_count     INT NOT NULL DEFAULT 0,
    auditor_id          VARCHAR(50),
    auditor_name        VARCHAR(50),
    audit_time          DATETIME2,
    reverse_by          VARCHAR(50),
    reverse_time        DATETIME2,
    reverse_reason      VARCHAR(500),
    remark              VARCHAR(500),
    create_time         DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time         DATETIME2,
    deleted             INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_pda_inbound_record_no UNIQUE (record_no)
);

CREATE INDEX idx_pda_inbound_record_status ON pda_inbound_record(status, erp_sync_status);
CREATE INDEX idx_pda_inbound_record_material ON pda_inbound_record(material_code, warehouse_code);

CREATE TABLE erp_sync_log (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    source_type         VARCHAR(30) NOT NULL,
    source_no           VARCHAR(30) NOT NULL,
    request_payload     NVARCHAR(MAX),
    response_payload    NVARCHAR(MAX),
    status              VARCHAR(20) NOT NULL,
    error_message       NVARCHAR(500),
    retry_count         INT NOT NULL DEFAULT 0,
    create_time         DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE INDEX idx_erp_sync_log_source ON erp_sync_log(source_type, source_no);
