-- 生产备料：会话记录金蝶建单人；PDA 消息通知建单人领料
ALTER TABLE pda_receive_scan_session ADD creator_kd_user_number VARCHAR(64) NULL;
ALTER TABLE pda_receive_scan_session ADD creator_kd_user_name VARCHAR(100) NULL;
ALTER TABLE pda_receive_scan_session ADD creator_wms_user_id VARCHAR(64) NULL;

CREATE TABLE pda_user_message (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    message_type    VARCHAR(32)  NOT NULL DEFAULT 'TASK',
    title           VARCHAR(200) NOT NULL,
    content         NVARCHAR(500),
    biz_type        VARCHAR(64),
    biz_no          VARCHAR(64),
    is_read         INT          NOT NULL DEFAULT 0,
    create_by       VARCHAR(50)  NOT NULL DEFAULT 'system',
    create_time     DATETIME2    NOT NULL DEFAULT GETDATE(),
    update_by       VARCHAR(50),
    update_time     DATETIME2,
    deleted         INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_pda_user_message_user ON pda_user_message(user_id, is_read, create_time);
