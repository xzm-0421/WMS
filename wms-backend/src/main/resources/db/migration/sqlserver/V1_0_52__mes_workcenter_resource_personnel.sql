-- 轻MES：工作中心 / 资源 / 人员 基础资料（金蝶同步只读）+ 报工人员字段

CREATE TABLE mes_work_center (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    erp_id              BIGINT,
    work_center_code    VARCHAR(50)  NOT NULL,
    work_center_name    VARCHAR(100) NOT NULL,
    work_shop_code      VARCHAR(50),
    work_shop_name      VARCHAR(100),
    dept_code           VARCHAR(50),
    dept_name           VARCHAR(100),
    capacity            DECIMAL(18,4),
    calendar_code       VARCHAR(50),
    calendar_name       VARCHAR(100),
    status              INT          NOT NULL DEFAULT 1,
    sync_status         VARCHAR(20)  NOT NULL DEFAULT 'SYNCED',
    last_sync_time      DATETIME2,
    fail_reason         NVARCHAR(500),
    create_by           VARCHAR(50)  NOT NULL DEFAULT 'system',
    create_time         DATETIME2    NOT NULL DEFAULT GETDATE(),
    update_by           VARCHAR(50),
    update_time         DATETIME2,
    deleted             INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_mes_work_center_code UNIQUE (work_center_code)
);

CREATE INDEX ix_mes_work_center_erp_id ON mes_work_center (erp_id);

CREATE TABLE mes_resource (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    erp_id              BIGINT,
    resource_code       VARCHAR(50)  NOT NULL,
    resource_name       VARCHAR(100) NOT NULL,
    resource_type_code  VARCHAR(50),
    resource_type       VARCHAR(20),
    work_center_code    VARCHAR(50),
    work_center_name    VARCHAR(100),
    capacity            DECIMAL(18,4),
    unit_code           VARCHAR(20),
    ref_type            VARCHAR(20),
    ref_code            VARCHAR(50),
    ref_name            VARCHAR(100),
    status              INT          NOT NULL DEFAULT 1,
    sync_status         VARCHAR(20)  NOT NULL DEFAULT 'SYNCED',
    last_sync_time      DATETIME2,
    fail_reason         NVARCHAR(500),
    create_by           VARCHAR(50)  NOT NULL DEFAULT 'system',
    create_time         DATETIME2    NOT NULL DEFAULT GETDATE(),
    update_by           VARCHAR(50),
    update_time         DATETIME2,
    deleted             INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_mes_resource_code UNIQUE (resource_code)
);

CREATE INDEX ix_mes_resource_erp_id ON mes_resource (erp_id);
CREATE INDEX ix_mes_resource_work_center ON mes_resource (work_center_code);

CREATE TABLE mes_personnel (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    erp_id              BIGINT,
    personnel_code      VARCHAR(50)  NOT NULL,
    personnel_name      VARCHAR(100) NOT NULL,
    dept_code           VARCHAR(50),
    dept_name           VARCHAR(100),
    post_code           VARCHAR(50),
    post_name           VARCHAR(100),
    skill_level         VARCHAR(50),
    work_center_code    VARCHAR(50),
    production_flag     INT          NOT NULL DEFAULT 1,
    sys_user_id         BIGINT,
    sys_username        VARCHAR(50),
    status              INT          NOT NULL DEFAULT 1,
    sync_status         VARCHAR(20)  NOT NULL DEFAULT 'SYNCED',
    last_sync_time      DATETIME2,
    fail_reason         NVARCHAR(500),
    create_by           VARCHAR(50)  NOT NULL DEFAULT 'system',
    create_time         DATETIME2    NOT NULL DEFAULT GETDATE(),
    update_by           VARCHAR(50),
    update_time         DATETIME2,
    deleted             INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_mes_personnel_code UNIQUE (personnel_code)
);

CREATE INDEX ix_mes_personnel_erp_id ON mes_personnel (erp_id);
CREATE INDEX ix_mes_personnel_work_center ON mes_personnel (work_center_code);

IF COL_LENGTH('mes_report', 'personnel_code') IS NULL
    ALTER TABLE mes_report ADD personnel_code VARCHAR(50);
IF COL_LENGTH('mes_report', 'personnel_name') IS NULL
    ALTER TABLE mes_report ADD personnel_name VARCHAR(100);
