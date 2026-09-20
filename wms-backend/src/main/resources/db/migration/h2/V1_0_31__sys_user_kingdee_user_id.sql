-- WMS 用户绑定金蝶用户，供工作流审批（WorkflowAudit）写入 UserId
ALTER TABLE sys_user ADD COLUMN kingdee_user_id BIGINT;
