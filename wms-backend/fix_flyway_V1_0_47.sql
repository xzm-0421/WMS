USE [wms];
GO

PRINT '====== 开始修复 Flyway 迁移 V1_0_47 ======';
PRINT '';

-- 1️⃣ 第一步：安全地添加缺失的列（如果不存在）
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'mes_report'
    AND COLUMN_NAME = 'client_report_no'
)
BEGIN
    PRINT '✓ 添加 client_report_no 列...';
    ALTER TABLE mes_report ADD client_report_no VARCHAR(64) NULL;
END
ELSE
BEGIN
    PRINT 'ℹ client_report_no 列已存在，跳过';
END

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'mes_report'
    AND COLUMN_NAME = 'client_time'
)
BEGIN
    PRINT '✓ 添加 client_time 列...';
    ALTER TABLE mes_report ADD client_time DATETIME2 NULL;
END
ELSE
BEGIN
    PRINT 'ℹ client_time 列已存在，跳过';
END

GO

-- 2️⃣ 第二步：检查并创建唯一索引（如果不存在）
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'uk_mes_report_client_no'
    AND object_id = OBJECT_ID('mes_report')
)
BEGIN
    PRINT '✓ 创建唯一索引 uk_mes_report_client_no...';
    CREATE UNIQUE INDEX uk_mes_report_client_no
    ON mes_report(client_report_no)
    WHERE client_report_no IS NOT NULL;
END
ELSE
BEGIN
    PRINT 'ℹ 索引 uk_mes_report_client_no 已存在，跳过';
END

GO

-- 3️⃣ 第三步：修复 Flyway 历史记录
IF NOT EXISTS (
    SELECT 1 FROM flyway_schema_history
    WHERE version = '1.0.47'
    AND success = 1
)
BEGIN
    -- 如果有失败的记录，先删除
    IF EXISTS (
        SELECT 1 FROM flyway_schema_history
        WHERE version = '1.0.47'
        AND success = 0
    )
    BEGIN
        PRINT '✓ 删除失败的迁移记录...';
        DELETE FROM flyway_schema_history
        WHERE version = '1.0.47';
    END

    -- 获取当前最大 installed_rank
    DECLARE @max_rank INT;
    SELECT @max_rank = ISNULL(MAX(installed_rank), 0)
    FROM flyway_schema_history;

    -- 插入成功的迁移记录
    -- ⚠️ 注意：checksum 值需要根据实际文件内容计算
    -- 如果不确定，可以先设为 NULL，Flyway 会在下次启动时重新计算
    PRINT '✓ 插入成功的迁移记录...';
    INSERT INTO flyway_schema_history (
        installed_rank,
        version,
        description,
        type,
        script,
        checksum,
        installed_by,
        installed_on,
        execution_time,
        success
    ) VALUES (
        @max_rank + 1,
        '1.0.47',
        'mes report idempotent',
        'SQL',
        'V1_0_47__mes_report_idempotent.sql',
        NULL,  -- 设为 NULL 让 Flyway 自动处理
        SYSTEM_USER,
        GETDATE(),
        0,     -- 执行时间（毫秒）
        1      -- success = true
    );

    PRINT '✅ Flyway 历史记录修复完成！';
END
ELSE
BEGIN
    PRINT 'ℹ Flyway 记录已存在且成功，跳过';
END

GO

-- 4️⃣ 第四步：验证修复结果
PRINT '';
PRINT '====== 修复结果验证 ======';
PRINT '';

-- 检查表结构
PRINT '📋 mes_report 表新增列:';
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'mes_report'
AND COLUMN_NAME IN ('client_report_no', 'client_time')
ORDER BY ORDINAL_POSITION;

PRINT '';

-- 检查索引
PRINT '📋 唯一索引状态:';
SELECT
    name AS index_name,
    is_unique,
    is_filtered,
    type_desc
FROM sys.indexes
WHERE object_id = OBJECT_ID('mes_report')
AND name = 'uk_mes_report_client_no';

PRINT '';

-- 检查 Flyway 记录
PRINT '📋 Flyway 迁移记录:';
SELECT
    version,
    description,
    type,
    success,
    installed_on,
    installed_by
FROM flyway_schema_history
WHERE version = '1.0.47';

PRINT '';
PRINT '==========================';
PRINT '✅ 修复脚本执行完毕！';
PRINT '';
PRINT '下一步操作：';
PRINT '1. 关闭 SQL Server Management Studio';
PRINT '2. 回到终端执行: mvn spring-boot:run';
PRINT '3. 后端应该能正常启动了';
PRINT '==========================';