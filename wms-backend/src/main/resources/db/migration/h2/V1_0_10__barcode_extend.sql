ALTER TABLE barcode_rule ADD version_no INT NOT NULL DEFAULT 1;
ALTER TABLE barcode_rule ADD template_code VARCHAR(50);
ALTER TABLE barcode_rule ADD separator VARCHAR(20) DEFAULT '';
ALTER TABLE barcode_rule ADD description VARCHAR(500);
ALTER TABLE barcode_rule ADD remark VARCHAR(500);
ALTER TABLE barcode_rule ADD create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE barcode_rule ADD update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE barcode_rule_version (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code     VARCHAR(50) NOT NULL,
    version_no    INT NOT NULL,
    rule_name     VARCHAR(100) NOT NULL,
    applies_to    VARCHAR(20),
    barcode_type  VARCHAR(20),
    template_code VARCHAR(50),
    separator     VARCHAR(20),
    segments_json CLOB,
    description   VARCHAR(500),
    status        INT NOT NULL DEFAULT 1,
    change_log    VARCHAR(500),
    created_by    VARCHAR(50),
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_barcode_rule_version UNIQUE (rule_code, version_no)
);

CREATE TABLE barcode_instance (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    barcode_content VARCHAR(200) NOT NULL,
    rule_code       VARCHAR(50),
    version_no      INT,
    material_code   VARCHAR(50),
    batch_no        VARCHAR(50),
    serial_no       VARCHAR(100),
    pack_barcode    VARCHAR(100),
    barcode_type    VARCHAR(20),
    source_type     VARCHAR(30) NOT NULL DEFAULT 'GENERATED',
    source_ref      VARCHAR(100),
    status          INT NOT NULL DEFAULT 1,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_barcode_instance_content UNIQUE (barcode_content)
);

CREATE INDEX idx_barcode_instance_material ON barcode_instance(material_code, batch_no);
CREATE INDEX idx_barcode_instance_serial ON barcode_instance(serial_no);

CREATE TABLE barcode_serial_registry (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    serial_no           VARCHAR(100) NOT NULL,
    material_code       VARCHAR(50),
    batch_no            VARCHAR(50),
    barcode_instance_id BIGINT,
    status              VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_barcode_serial_no UNIQUE (serial_no)
);

CREATE TABLE barcode_trace_link (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    barcode_content     VARCHAR(200) NOT NULL,
    barcode_instance_id BIGINT,
    ref_type            VARCHAR(30) NOT NULL,
    ref_no              VARCHAR(50),
    transaction_no      VARCHAR(50),
    material_code       VARCHAR(50),
    batch_no            VARCHAR(50),
    serial_no           VARCHAR(100),
    remark              VARCHAR(500),
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_barcode_trace_content ON barcode_trace_link(barcode_content);
CREATE INDEX idx_barcode_trace_ref ON barcode_trace_link(ref_type, ref_no);
