USE [wms];
GO

PRINT 'Step 1: Adding columns...';
ALTER TABLE mes_report ADD client_report_no VARCHAR(64) NULL;
ALTER TABLE mes_report ADD client_time DATETIME2 NULL;
GO

PRINT 'Step 2: Creating index...';
CREATE UNIQUE INDEX uk_mes_report_client_no ON mes_report(client_report_no) WHERE client_report_no IS NOT NULL;
GO

PRINT 'Step 3: Fixing Flyway history...';
DECLARE @max_rank INT;
SELECT @max_rank = ISNULL(MAX(installed_rank), 0) FROM flyway_schema_history;

INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES (@max_rank + 1, '1.0.47', 'mes report idempotent', 'SQL', 'V1_0_47__mes_report_idempotent.sql', NULL, SYSTEM_USER, GETDATE(), 0, 1);
GO

PRINT 'Done! Verification:';
SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'mes_report' AND COLUMN_NAME IN ('client_report_no', 'client_time');
SELECT version, success FROM flyway_schema_history WHERE version = '1.0.47';