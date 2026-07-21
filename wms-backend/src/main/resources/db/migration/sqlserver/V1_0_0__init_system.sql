-- V1.0.0 系统管理表
CREATE TABLE sys_user (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    password        VARCHAR(200) NOT NULL,
    real_name       VARCHAR(50)  NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(100),
    dept_id         BIGINT,
    status          INT          NOT NULL DEFAULT 1,
    warehouse_scope_json VARCHAR(500),
    create_by       VARCHAR(50)  NOT NULL DEFAULT 'system',
    create_time     DATETIME2    NOT NULL DEFAULT GETDATE(),
    update_by       VARCHAR(50),
    update_time     DATETIME2,
    deleted         INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE sys_role (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    role_code       VARCHAR(50)  NOT NULL,
    role_name       VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    data_scope      INT          DEFAULT 1,
    status          INT          NOT NULL DEFAULT 1,
    create_by       VARCHAR(50)  NOT NULL DEFAULT 'system',
    create_time     DATETIME2    NOT NULL DEFAULT GETDATE(),
    update_by       VARCHAR(50),
    update_time     DATETIME2,
    deleted         INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

CREATE TABLE sys_permission (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    module          VARCHAR(50),
    status          INT          NOT NULL DEFAULT 1,
    create_by       VARCHAR(50)  NOT NULL DEFAULT 'system',
    create_time     DATETIME2    NOT NULL DEFAULT GETDATE(),
    update_by       VARCHAR(50),
    update_time     DATETIME2,
    deleted         INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
);

CREATE TABLE sys_user_role (
    id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id  BIGINT NOT NULL,
    role_id  BIGINT NOT NULL
);

CREATE TABLE sys_role_permission (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL
);

CREATE TABLE sys_org (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_code    VARCHAR(50)  NOT NULL,
    org_name    VARCHAR(100) NOT NULL,
    parent_id   BIGINT,
    status      INT NOT NULL DEFAULT 1,
    create_by   VARCHAR(50) NOT NULL DEFAULT 'system',
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted     INT NOT NULL DEFAULT 0
);

CREATE TABLE sys_operation_log (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    operator_id     VARCHAR(50),
    operator_name   VARCHAR(50),
    module          VARCHAR(50),
    operation_type  VARCHAR(20),
    operation_content VARCHAR(500),
    request_params  NVARCHAR(MAX),
    response_result VARCHAR(20),
    ip_address      VARCHAR(50),
    device_info     VARCHAR(200),
    operation_time  DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE TABLE sys_dict (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    dict_code   VARCHAR(50) NOT NULL,
    dict_name   VARCHAR(100) NOT NULL,
    status      INT NOT NULL DEFAULT 1,
    create_by   VARCHAR(50) NOT NULL DEFAULT 'system',
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted     INT NOT NULL DEFAULT 0
);

CREATE TABLE sys_dict_item (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    dict_code   VARCHAR(50) NOT NULL,
    item_value  VARCHAR(100) NOT NULL,
    item_label  VARCHAR(100) NOT NULL,
    sort_order  INT DEFAULT 0,
    status      INT NOT NULL DEFAULT 1
);
