-- WMS 用户 ↔ 金蝶用户映射（业务键 kd_user_number，API 加速字段 kd_user_id）
CREATE TABLE sys_user_kingdee_map (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_user_id    VARCHAR(64)  NOT NULL,
    kd_user_number      VARCHAR(64)  NOT NULL,
    kd_user_id          BIGINT,
    remark              VARCHAR(200),
    status              INT          NOT NULL DEFAULT 1,
    create_by           VARCHAR(50)  NOT NULL DEFAULT 'system',
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(50),
    update_time         TIMESTAMP,
    deleted             INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_user_kingdee_ext UNIQUE (external_user_id)
);

CREATE INDEX idx_sys_user_kingdee_number ON sys_user_kingdee_map(kd_user_number);

-- 若曾在 sys_user 上加过 kingdee_user_id，迁移后删除该列
ALTER TABLE sys_user DROP COLUMN IF EXISTS kingdee_user_id;
