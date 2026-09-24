-- 轻 MES：移动端离线报工幂等键与客户端时间

ALTER TABLE mes_report ADD client_report_no VARCHAR(64) NULL;
ALTER TABLE mes_report ADD client_time DATETIME2 NULL;

CREATE UNIQUE INDEX uk_mes_report_client_no ON mes_report(client_report_no) WHERE client_report_no IS NOT NULL;
