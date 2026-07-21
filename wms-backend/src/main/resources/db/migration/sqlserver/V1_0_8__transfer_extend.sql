-- V1.0.8 调拨单扩展 + 发料单明细补全字段
IF COL_LENGTH('transfer_order', 'creator_name') IS NULL
    ALTER TABLE transfer_order ADD creator_name VARCHAR(50);
IF COL_LENGTH('transfer_order', 'remark') IS NULL
    ALTER TABLE transfer_order ADD remark VARCHAR(500);
IF COL_LENGTH('transfer_order', 'create_time') IS NULL
    ALTER TABLE transfer_order ADD create_time DATETIME2 NOT NULL DEFAULT GETDATE();
IF COL_LENGTH('transfer_order', 'deleted') IS NULL
    ALTER TABLE transfer_order ADD deleted INT NOT NULL DEFAULT 0;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'pick_issue_line')
BEGIN
    CREATE TABLE pick_issue_line (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        issue_no        VARCHAR(30) NOT NULL,
        line_no         INT NOT NULL,
        material_code   VARCHAR(50) NOT NULL,
        pick_qty        DECIMAL(18,4) NOT NULL,
        picked_qty      DECIMAL(18,4) DEFAULT 0,
        source_location VARCHAR(50),
        batch_no        VARCHAR(50)
    );
END
