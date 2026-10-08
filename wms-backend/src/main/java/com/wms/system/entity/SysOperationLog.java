package com.wms.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统操作日志（AOP 记录所有关键操作，表在 init_system.sql 已建）。
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String operatorId;
    private String operatorName;
    private String module;
    private String operationType;
    private String operationContent;
    private String requestParams;
    private String responseResult;
    private String ipAddress;
    private String deviceInfo;
    private LocalDateTime operationTime;
}
