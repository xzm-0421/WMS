CREATE TABLE pda_receive_scan_session (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no             VARCHAR(64) NOT NULL,
    supplier_code       VARCHAR(64),
    supplier_name       VARCHAR(200),
    bill_date           DATE,
    warehouse_code      VARCHAR(50) DEFAULT 'WH01',
    status              VARCHAR(32) NOT NULL DEFAULT 'SCANNING',
    total_lines         INT NOT NULL DEFAULT 0,
    checked_lines       INT NOT NULL DEFAULT 0,
    submitted_lines     INT NOT NULL DEFAULT 0,
    device_no           VARCHAR(50),
    operator_id         VARCHAR(50),
    operator_name       VARCHAR(50),
    last_scan_time      TIMESTAMP,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    CONSTRAINT uk_pda_receive_session_bill UNIQUE (bill_no)
);

CREATE TABLE pda_receive_scan_line (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT NOT NULL,
    bill_no             VARCHAR(64) NOT NULL,
    line_no             INT NOT NULL,
    material_code       VARCHAR(64) NOT NULL,
    material_name       VARCHAR(200),
    specification       VARCHAR(200),
    batch_no            VARCHAR(64),
    plan_qty            DECIMAL(18,4) NOT NULL DEFAULT 0,
    checked             INT NOT NULL DEFAULT 0,
    scanned_qty         DECIMAL(18,4) NOT NULL DEFAULT 0,
    submitted_qty       DECIMAL(18,4) NOT NULL DEFAULT 0,
    unit_code           VARCHAR(20),
    scanned_barcode     VARCHAR(200),
    last_scan_time      TIMESTAMP,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    CONSTRAINT uk_pda_receive_line UNIQUE (bill_no, line_no)
);

CREATE TABLE pda_receive_submit_batch (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no            VARCHAR(30) NOT NULL,
    bill_no             VARCHAR(64) NOT NULL,
    line_count          INT NOT NULL DEFAULT 0,
    total_qty           DECIMAL(18,4) NOT NULL DEFAULT 0,
    erp_sync_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    erp_bill_no         VARCHAR(64),
    erp_sync_message    VARCHAR(500),
    operator_id         VARCHAR(50),
    operator_name       VARCHAR(50),
    device_no           VARCHAR(50),
    submit_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pda_receive_submit_batch UNIQUE (batch_no)
);

ALTER TABLE pda_inbound_record ADD COLUMN source_type VARCHAR(30);
ALTER TABLE pda_inbound_record ADD COLUMN source_bill_no VARCHAR(64);
ALTER TABLE pda_inbound_record ADD COLUMN source_line_no INT;
ALTER TABLE pda_inbound_record ADD COLUMN submit_batch_no VARCHAR(30);
