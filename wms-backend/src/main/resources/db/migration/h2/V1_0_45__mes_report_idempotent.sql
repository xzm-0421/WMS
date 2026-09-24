-- 轻 MES：移动端离线报工幂等键与客户端时间

ALTER TABLE mes_report ADD COLUMN client_report_no VARCHAR(64);
ALTER TABLE mes_report ADD COLUMN client_time TIMESTAMP;

CREATE UNIQUE INDEX uk_mes_report_client_no ON mes_report(client_report_no);
