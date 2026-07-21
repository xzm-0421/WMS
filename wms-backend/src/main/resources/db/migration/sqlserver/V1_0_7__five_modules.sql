-- V1.0.7 五模块扩展：基础数据增强 + 来料 + 拣配 + 库存扩展 + 审计规则

-- ========== 基础数据字段扩展 ==========
IF COL_LENGTH('base_warehouse', 'area_sqm') IS NULL
    ALTER TABLE base_warehouse ADD area_sqm DECIMAL(18,2);
IF COL_LENGTH('base_warehouse', 'rated_capacity') IS NULL
    ALTER TABLE base_warehouse ADD rated_capacity DECIMAL(18,4);
IF COL_LENGTH('base_warehouse', 'geo_location') IS NULL
    ALTER TABLE base_warehouse ADD geo_location VARCHAR(200);

IF COL_LENGTH('base_location', 'occupy_status') IS NULL
    ALTER TABLE base_location ADD occupy_status VARCHAR(20) NOT NULL DEFAULT 'IDLE';
IF COL_LENGTH('base_location', 'qr_code') IS NULL
    ALTER TABLE base_location ADD qr_code VARCHAR(200);

IF COL_LENGTH('base_material', 'production_date') IS NULL
    ALTER TABLE base_material ADD production_date DATE;
IF COL_LENGTH('base_material', 'default_batch_no') IS NULL
    ALTER TABLE base_material ADD default_batch_no VARCHAR(50);

-- ========== 模块二：来料管理 ==========
CREATE TABLE purchase_order (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_no        VARCHAR(30) NOT NULL,
    supplier_code   VARCHAR(50) NOT NULL,
    warehouse_code  VARCHAR(50) NOT NULL,
    plan_arrive_date DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_qty       DECIMAL(18,4) DEFAULT 0,
    received_qty    DECIMAL(18,4) DEFAULT 0,
    creator_name    VARCHAR(50),
    remark          VARCHAR(500),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time     DATETIME2,
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_order_no UNIQUE (order_no)
);

CREATE TABLE purchase_order_line (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_no        VARCHAR(30) NOT NULL,
    line_no         INT NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    material_name   VARCHAR(200),
    unit_code       VARCHAR(20),
    order_qty       DECIMAL(18,4) NOT NULL,
    received_qty    DECIMAL(18,4) DEFAULT 0,
    batch_no        VARCHAR(50),
    line_status     VARCHAR(20) DEFAULT 'OPEN'
);

CREATE TABLE delivery_note (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    delivery_no     VARCHAR(30) NOT NULL,
    purchase_order_no VARCHAR(30),
    supplier_code   VARCHAR(50) NOT NULL,
    delivery_date   DATETIME2,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    diff_flag       INT DEFAULT 0,
    remark          VARCHAR(500),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_delivery_no UNIQUE (delivery_no)
);

CREATE TABLE delivery_note_line (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    delivery_no     VARCHAR(30) NOT NULL,
    line_no         INT NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    plan_qty        DECIMAL(18,4) DEFAULT 0,
    actual_qty      DECIMAL(18,4) DEFAULT 0,
    diff_qty        DECIMAL(18,4) DEFAULT 0
);

CREATE TABLE purchase_return (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    return_no       VARCHAR(30) NOT NULL,
    receipt_ref_no  VARCHAR(30),
    supplier_code   VARCHAR(50),
    reason          VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    creator_name    VARCHAR(50),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_return_no UNIQUE (return_no)
);

CREATE TABLE purchase_return_line (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    return_no       VARCHAR(30) NOT NULL,
    line_no         INT NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    return_qty      DECIMAL(18,4) NOT NULL,
    batch_no        VARCHAR(50)
);

-- ========== 模块三：拣配管理 ==========
CREATE TABLE prep_notice (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    notice_no       VARCHAR(30) NOT NULL,
    production_plan_no VARCHAR(30),
    warehouse_code  VARCHAR(50) NOT NULL,
    demand_time     DATETIME2,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    creator_name    VARCHAR(50),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_prep_notice_no UNIQUE (notice_no)
);

CREATE TABLE prep_notice_line (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    notice_no       VARCHAR(30) NOT NULL,
    line_no         INT NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    material_name   VARCHAR(200),
    demand_qty      DECIMAL(18,4) NOT NULL,
    picked_qty      DECIMAL(18,4) DEFAULT 0,
    recommend_loc   VARCHAR(50)
);

CREATE TABLE pick_issue (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    issue_no        VARCHAR(30) NOT NULL,
    notice_no       VARCHAR(30),
    warehouse_code  VARCHAR(50) NOT NULL,
    handover_area   VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'PICKING',
    picker_name     VARCHAR(50),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_pick_issue_no UNIQUE (issue_no)
);

CREATE TABLE pick_issue_line (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    issue_no        VARCHAR(30) NOT NULL,
    line_no         INT NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    pick_qty        DECIMAL(18,4) NOT NULL,
    picked_qty      DECIMAL(18,4) DEFAULT 0,
    source_location VARCHAR(50),
    batch_no        VARCHAR(50)
);

CREATE TABLE material_pickup (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    pickup_no       VARCHAR(30) NOT NULL,
    issue_no        VARCHAR(30) NOT NULL,
    receiver_name   VARCHAR(50),
    pickup_time     DATETIME2,
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    CONSTRAINT uk_material_pickup_no UNIQUE (pickup_no)
);

CREATE TABLE workshop_return (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    return_no       VARCHAR(30) NOT NULL,
    issue_no        VARCHAR(30),
    warehouse_code  VARCHAR(50) NOT NULL,
    return_reason   VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    operator_name   VARCHAR(50),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_workshop_return_no UNIQUE (return_no)
);

CREATE TABLE workshop_return_line (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    return_no       VARCHAR(30) NOT NULL,
    line_no         INT NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    return_qty      DECIMAL(18,4) NOT NULL,
    target_location VARCHAR(50),
    batch_no        VARCHAR(50)
);

-- ========== 模块四：库存扩展 ==========
CREATE TABLE other_inbound (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_no        VARCHAR(30) NOT NULL,
    inbound_type    VARCHAR(20) NOT NULL,
    warehouse_code  VARCHAR(50) NOT NULL,
    source_desc     VARCHAR(200),
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    creator_name    VARCHAR(50),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_other_inbound_no UNIQUE (order_no)
);

CREATE TABLE other_inbound_line (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_no        VARCHAR(30) NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    material_name   VARCHAR(200),
    quantity        DECIMAL(18,4) NOT NULL,
    location_code   VARCHAR(50),
    batch_no        VARCHAR(50)
);

CREATE TABLE other_outbound (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_no        VARCHAR(30) NOT NULL,
    outbound_type   VARCHAR(20) NOT NULL,
    warehouse_code  VARCHAR(50) NOT NULL,
    target_desc     VARCHAR(200),
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    creator_name    VARCHAR(50),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_other_outbound_no UNIQUE (order_no)
);

CREATE TABLE other_outbound_line (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_no        VARCHAR(30) NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    material_name   VARCHAR(200),
    quantity        DECIMAL(18,4) NOT NULL,
    location_code   VARCHAR(50),
    batch_no        VARCHAR(50)
);

CREATE TABLE sample_plan (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    plan_no         VARCHAR(30) NOT NULL,
    warehouse_code  VARCHAR(50) NOT NULL,
    plan_date       DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    creator_name    VARCHAR(50),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE(),
    deleted         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sample_plan_no UNIQUE (plan_no)
);

CREATE TABLE sample_result (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    plan_no         VARCHAR(30) NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    batch_no        VARCHAR(50),
    location_code   VARCHAR(50),
    quality_status  VARCHAR(20),
    expire_date     DATE,
    alert_flag      INT DEFAULT 0,
    remark          VARCHAR(500),
    check_time      DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- ========== 系统规则与审计 ==========
CREATE TABLE wms_business_rule (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    rule_code       VARCHAR(50) NOT NULL,
    rule_name       VARCHAR(100) NOT NULL,
    rule_value      VARCHAR(200),
    enabled         INT NOT NULL DEFAULT 1,
    remark          VARCHAR(500),
    CONSTRAINT uk_wms_rule_code UNIQUE (rule_code)
);

CREATE TABLE wms_audit_trail (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    biz_type        VARCHAR(50) NOT NULL,
    biz_no          VARCHAR(50) NOT NULL,
    action          VARCHAR(50) NOT NULL,
    operator_id     VARCHAR(50),
    operator_name   VARCHAR(50),
    detail_json     NVARCHAR(MAX),
    create_time     DATETIME2 NOT NULL DEFAULT GETDATE()
);

INSERT INTO wms_business_rule (rule_code, rule_name, rule_value, remark) VALUES
('OVER_RECEIVE_TOLERANCE', '超量收货容差比例', '0.05', '收货数量不得超过订单数量*(1+容差)'),
('OVER_ISSUE_TOLERANCE', '超量发料容差比例', '0.02', '发料数量不得超过需求数量*(1+容差)'),
('MIX_BATCH_FORBID', '同库位禁止混批次', '1', '1=启用防混料');
