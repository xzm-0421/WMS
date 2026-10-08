-- 阶段C（安全/埋点/告警）：密码过期时间 + 事件埋点表 + 告警表

-- C2.4 密码 90 天过期：记录最近一次密码修改时间
IF COL_LENGTH('sys_user', 'pwd_updated_at') IS NULL
    ALTER TABLE sys_user ADD pwd_updated_at DATETIME2;
UPDATE sys_user SET pwd_updated_at = GETDATE() WHERE pwd_updated_at IS NULL;

-- C3.1 数据埋点事件表
IF OBJECT_ID('mes_event_log', 'U') IS NULL
CREATE TABLE mes_event_log (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    event_name      VARCHAR(50)  NOT NULL,
    biz_type        VARCHAR(30),
    biz_no          VARCHAR(50),
    operator_id     VARCHAR(50),
    operator_name   VARCHAR(100),
    properties      NVARCHAR(MAX),
    event_time      DATETIME2 NOT NULL DEFAULT GETDATE(),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE()
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_mes_event_log_name_time' AND object_id = OBJECT_ID('mes_event_log'))
    CREATE INDEX ix_mes_event_log_name_time ON mes_event_log (event_name, event_time);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_mes_event_log_biz_no' AND object_id = OBJECT_ID('mes_event_log'))
    CREATE INDEX ix_mes_event_log_biz_no ON mes_event_log (biz_no);

-- C4.1 告警表（队列满 / SLA / 长期未同步）
IF OBJECT_ID('sys_alert', 'U') IS NULL
CREATE TABLE sys_alert (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    alert_type      VARCHAR(30)  NOT NULL,
    level           VARCHAR(10)  NOT NULL DEFAULT 'WARN',
    title           VARCHAR(200) NOT NULL,
    content         VARCHAR(1000),
    biz_no          VARCHAR(50),
    status          VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    alert_count     INT NOT NULL DEFAULT 1,
    first_time      DATETIME2 NOT NULL DEFAULT GETDATE(),
    last_time       DATETIME2 NOT NULL DEFAULT GETDATE(),
    ack_by          VARCHAR(50),
    ack_time        DATETIME2,
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time     DATETIME2
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_sys_alert_status' AND object_id = OBJECT_ID('sys_alert'))
    CREATE INDEX ix_sys_alert_status ON sys_alert (status, alert_type);
