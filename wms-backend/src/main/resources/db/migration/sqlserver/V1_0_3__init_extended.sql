-- V1.0.3 盘点/质检/生产扩展表
CREATE TABLE stockcheck_plan (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    plan_no         VARCHAR(30) NOT NULL,
    plan_name       VARCHAR(100) NOT NULL,
    warehouse_code  VARCHAR(50) NOT NULL,
    plan_type       VARCHAR(20) NOT NULL DEFAULT 'FULL',
    plan_date       DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    creator_id      VARCHAR(50) NOT NULL,
    creator_name    VARCHAR(50) NOT NULL,
    remark          VARCHAR(500),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time     DATETIME2,
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_stockcheck_plan_no UNIQUE (plan_no)
);

CREATE TABLE stockcheck_task (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    task_no         VARCHAR(30) NOT NULL,
    plan_no         VARCHAR(30) NOT NULL,
    warehouse_code  VARCHAR(50) NOT NULL,
    location_code   VARCHAR(50),
    assignee_id     VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    complete_time   DATETIME2,
    CONSTRAINT uk_stockcheck_task_no UNIQUE (task_no)
);

CREATE TABLE stockcheck_detail (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    task_no         VARCHAR(30) NOT NULL,
    line_no         INT NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    location_code   VARCHAR(50) NOT NULL,
    batch_no        VARCHAR(50) DEFAULT '',
    book_qty        DECIMAL(18,4) NOT NULL DEFAULT 0,
    actual_qty      DECIMAL(18,4),
    diff_qty        DECIMAL(18,4),
    line_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE stockcheck_diff (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    task_no         VARCHAR(30) NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    location_code   VARCHAR(50) NOT NULL,
    batch_no        VARCHAR(50) DEFAULT '',
    diff_qty        DECIMAL(18,4) NOT NULL,
    diff_reason     VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approver_id     VARCHAR(50),
    approve_time    DATETIME2,
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE TABLE qc_standard (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    standard_code   VARCHAR(50) NOT NULL,
    standard_name   VARCHAR(100) NOT NULL,
    material_code   VARCHAR(50),
    check_items     NVARCHAR(MAX),
    status          INT NOT NULL DEFAULT 1,
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_qc_standard_code UNIQUE (standard_code)
);

CREATE TABLE qc_order (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    qc_no           VARCHAR(30) NOT NULL,
    source_type     VARCHAR(20) NOT NULL,
    source_no       VARCHAR(30) NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    batch_no        VARCHAR(50) DEFAULT '',
    sample_qty      DECIMAL(18,4) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result          VARCHAR(20),
    inspector_id    VARCHAR(50),
    inspector_name  VARCHAR(50),
    inspect_time    DATETIME2,
    remark          VARCHAR(500),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    CONSTRAINT uk_qc_order_no UNIQUE (qc_no)
);

CREATE TABLE production_order (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_no        VARCHAR(30) NOT NULL,
    product_code    VARCHAR(50) NOT NULL,
    product_name    VARCHAR(200),
    plan_qty        DECIMAL(18,4) NOT NULL,
    completed_qty   DECIMAL(18,4) DEFAULT 0,
    plan_start      DATE,
    plan_end        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    warehouse_code  VARCHAR(50),
    remark          VARCHAR(500),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_production_order_no UNIQUE (order_no)
);

CREATE TABLE bom_header (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    bom_code        VARCHAR(50) NOT NULL,
    product_code    VARCHAR(50) NOT NULL,
    version_no      VARCHAR(20) NOT NULL DEFAULT 'V1',
    status          INT NOT NULL DEFAULT 1,
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    CONSTRAINT uk_bom_code UNIQUE (bom_code)
);

CREATE TABLE bom_detail (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    bom_code        VARCHAR(50) NOT NULL,
    line_no         INT NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    material_name   VARCHAR(200),
    unit_code       VARCHAR(20) NOT NULL,
    qty_per         DECIMAL(18,4) NOT NULL
);

CREATE TABLE safety_stock (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    warehouse_code  VARCHAR(50) NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    safety_qty      DECIMAL(18,4) NOT NULL DEFAULT 0,
    max_qty         DECIMAL(18,4),
    status          INT NOT NULL DEFAULT 1,
    CONSTRAINT uk_safety_stock UNIQUE (warehouse_code, material_code)
);
