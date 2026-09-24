-- 质检模块：质检标准与质检单扩展（物料名称/质检类型/判定/让步接收）

ALTER TABLE qc_standard ADD material_name NVARCHAR(200) NULL;
ALTER TABLE qc_standard ADD qc_type VARCHAR(20) NULL;

ALTER TABLE qc_order ADD material_name NVARCHAR(200) NULL;
ALTER TABLE qc_order ADD qc_type VARCHAR(20) NULL;
ALTER TABLE qc_order ADD qc_qty DECIMAL(18,4) NULL;
ALTER TABLE qc_order ADD qualified_qty DECIMAL(18,4) NULL;
ALTER TABLE qc_order ADD unqualified_qty DECIMAL(18,4) NULL;
ALTER TABLE qc_order ADD judge_result VARCHAR(20) NULL;
ALTER TABLE qc_order ADD judge_remark VARCHAR(500) NULL;
ALTER TABLE qc_order ADD judge_time DATETIME2 NULL;
ALTER TABLE qc_order ADD judge_by VARCHAR(50) NULL;
ALTER TABLE qc_order ADD concession_reason VARCHAR(500) NULL;
ALTER TABLE qc_order ADD concession_approver VARCHAR(50) NULL;
ALTER TABLE qc_order ADD concession_time DATETIME2 NULL;
