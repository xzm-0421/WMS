-- 轻 MES：返工汇合工序标识（返工序列在该工序结束）

ALTER TABLE mes_process ADD is_converge_op TINYINT NOT NULL DEFAULT 0;
ALTER TABLE mes_route_op ADD is_converge_op TINYINT NOT NULL DEFAULT 0;
