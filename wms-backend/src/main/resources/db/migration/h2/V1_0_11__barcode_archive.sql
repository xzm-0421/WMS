CREATE TABLE barcode_archive (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_no          VARCHAR(50) NOT NULL,
    barcode_content     VARCHAR(200) NOT NULL,
    barcode_instance_id BIGINT,
    rule_code           VARCHAR(50),
    version_no          INT,
    material_code       VARCHAR(50),
    batch_no            VARCHAR(50),
    serial_no           VARCHAR(100),
    pack_barcode        VARCHAR(100),
    barcode_type        VARCHAR(20),
    template_code       VARCHAR(50),
    label_format        VARCHAR(30) NOT NULL DEFAULT 'barcode_archive',
    label_width_mm      DECIMAL(6, 2) NOT NULL DEFAULT 100,
    label_height_mm     DECIMAL(6, 2) NOT NULL DEFAULT 60,
    print_params_json   CLOB,
    action_type         VARCHAR(20) NOT NULL DEFAULT 'GENERATE',
    reprint_count       INT NOT NULL DEFAULT 0,
    last_reprint_time   TIMESTAMP,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_barcode_archive_no UNIQUE (archive_no)
);

CREATE INDEX idx_barcode_archive_content ON barcode_archive(barcode_content);
CREATE INDEX idx_barcode_archive_material ON barcode_archive(material_code);
CREATE INDEX idx_barcode_archive_create_time ON barcode_archive(create_time);
