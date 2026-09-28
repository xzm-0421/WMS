-- 轻 MES：移动端离线报工幂等键与客户端时间

-- 步骤1: 添加列
ALTER TABLE mes_report ADD client_report_no VARCHAR(64) NULL;
ALTER TABLE mes_report ADD client_time DATETIME2 NULL;
GO

-- 步骤2: 创建唯一索引 (需要在新批次中执行)
CREATE UNIQUE INDEX uk_mes_report_client_no ON mes_report(client_report_no) WHERE client_report_no IS NOT NULL;
