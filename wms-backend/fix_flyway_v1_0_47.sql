-- ========================================
-- Flyway 迁移失败修复脚本
-- 版本: V1_0_47__mes_report_idempotent
-- 日期: 2026-09-24
-- ========================================

-- 步骤1: 查看当前 Flyway 迁移状态
PRINT '========== 当前 Flyway 迁移状态 ==========';
SELECT
    installed_rank,
    version,
    description,
    type,
    script,
    installed_on,
    execution_time,
    success
FROM flyway_schema_history
WHERE version >= '1.0.45'
ORDER BY installed_rank DESC;
GO

-- 步骤2: 检查 mes_report 表当前状态
PRINT '========== 检查 mes_report 表结构 ==========';
SELECT
    COLUMN_NAME as [列名],
    DATA_TYPE as [数据类型],
    CHARACTER_MAXIMUM_LENGTH as [最大长度],
    IS_NULLABLE as [允许NULL]
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'mes_report'
AND COLUMN_NAME IN ('client_report_no', 'client_time');
GO

-- 步骤3: 检查索引是否存在
PRINT '========== 检查索引状态 ==========';
SELECT
    i.name as [索引名],
    i.type_desc as [索引类型],
    i.is_unique as [唯一索引]
FROM sys.indexes i
WHERE i.object_id = OBJECT_ID('mes_report')
AND i.name = 'uk_mes_report_client_no';
GO

-- 步骤4: 删除失败的 Flyway 迁移记录
PRINT '========== 删除失败的迁移记录 ==========';
DELETE FROM flyway_schema_history
WHERE version = '1.0.47'
AND success = 0;

-- 显示删除结果
IF @@ROWCOUNT > 0
    PRINT '成功删除 ' + CAST(@@ROWCOUNT AS VARCHAR) + ' 条失败记录';
ELSE
    PRINT '未找到需要删除的失败记录';
GO

-- 步骤5: 清理可能残留的列和索引 (如果迁移部分成功)
PRINT '========== 清理可能的残留对象 ==========';

-- 删除索引 (如果存在)
IF EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('mes_report')
    AND name = 'uk_mes_report_client_no'
)
BEGIN
    DROP INDEX uk_mes_report_client_no ON mes_report;
    PRINT '已删除索引: uk_mes_report_client_no';
END
ELSE
    PRINT '索引不存在,无需删除';
GO

-- 删除列 client_time (如果存在)
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'mes_report'
    AND COLUMN_NAME = 'client_time'
)
BEGIN
    ALTER TABLE mes_report DROP COLUMN client_time;
    PRINT '已删除列: client_time';
END
ELSE
    PRINT '列 client_time 不存在,无需删除';
GO

-- 删除列 client_report_no (如果存在)
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'mes_report'
    AND COLUMN_NAME = 'client_report_no'
)
BEGIN
    ALTER TABLE mes_report DROP COLUMN client_report_no;
    PRINT '已删除列: client_report_no';
END
ELSE
    PRINT '列 client_report_no 不存在,无需删除';
GO

-- 步骤6: 验证清理结果
PRINT '========== 清理后的状态验证 ==========';

-- 验证 Flyway 记录已删除
SELECT
    COUNT(*) as [失败记录数]
FROM flyway_schema_history
WHERE version = '1.0.47'
AND success = 0;

-- 验证列已删除
SELECT
    COUNT(*) as [残留列数]
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'mes_report'
AND COLUMN_NAME IN ('client_report_no', 'client_time');

-- 验证索引已删除
SELECT
    COUNT(*) as [残留索引数]
FROM sys.indexes
WHERE object_id = OBJECT_ID('mes_report')
AND name = 'uk_mes_report_client_no';
GO

PRINT '========================================';
PRINT '清理完成!';
PRINT '现在可以重新启动 Spring Boot 应用';
PRINT 'Flyway 将重新执行修复后的 V1_0_47 迁移脚本';
PRINT '========================================';
