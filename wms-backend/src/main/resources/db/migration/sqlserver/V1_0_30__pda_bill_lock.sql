-- PDA 单据排他锁：同一单据同一时间仅允许一个用户操作
CREATE TABLE pda_bill_lock (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    bill_type           VARCHAR(64) NOT NULL,
    bill_no             VARCHAR(64) NOT NULL,
    lock_user_id        VARCHAR(50) NOT NULL,
    lock_user_name      NVARCHAR(100),
    lock_device_no      VARCHAR(50),
    lock_expire_time    DATETIME2 NOT NULL,
    create_time         DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time         DATETIME2,
    CONSTRAINT uk_pda_bill_lock UNIQUE (bill_type, bill_no)
);

CREATE INDEX idx_pda_bill_lock_expire ON pda_bill_lock(lock_expire_time);
CREATE INDEX idx_pda_bill_lock_user ON pda_bill_lock(lock_user_id);
