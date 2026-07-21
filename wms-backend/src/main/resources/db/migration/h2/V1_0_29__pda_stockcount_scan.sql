-- PDA 金蝶盘点作业扫码会话（断点续盘）
CREATE TABLE pda_stockcount_session (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no             VARCHAR(64) NOT NULL,
    bill_date           DATE,
    warehouse_code      VARCHAR(50),
    stock_org_code      VARCHAR(50),
    remark              VARCHAR(500),
    status              VARCHAR(32) NOT NULL DEFAULT 'COUNTING',
    total_lines         INT NOT NULL DEFAULT 0,
    counted_lines       INT NOT NULL DEFAULT 0,
    device_no           VARCHAR(50),
    operator_id         VARCHAR(50),
    operator_name       VARCHAR(50),
    last_scan_time      TIMESTAMP,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    CONSTRAINT uk_pda_stockcount_session_bill UNIQUE (bill_no)
);

CREATE INDEX idx_pda_stockcount_session_status ON pda_stockcount_session(status, update_time);

CREATE TABLE pda_stockcount_line (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT NOT NULL,
    bill_no             VARCHAR(64) NOT NULL,
    line_no             INT NOT NULL,
    entry_id            BIGINT,
    material_code       VARCHAR(64) NOT NULL,
    material_name       VARCHAR(200),
    specification       VARCHAR(200),
    warehouse_code      VARCHAR(50),
    location_code       VARCHAR(64),
    batch_no            VARCHAR(64),
    unit_code           VARCHAR(20),
    book_qty            DECIMAL(18,4) NOT NULL DEFAULT 0,
    actual_qty          DECIMAL(18,4),
    line_status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    scanned_barcode     VARCHAR(200),
    last_scan_time      TIMESTAMP,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    CONSTRAINT uk_pda_stockcount_line UNIQUE (bill_no, line_no)
);

CREATE INDEX idx_pda_stockcount_line_material ON pda_stockcount_line(bill_no, material_code);
