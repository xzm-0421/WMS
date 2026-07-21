package com.wms.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("erp_sync_log")
public class ErpSyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private String sourceNo;
    private String requestPayload;
    private String responsePayload;
    private String status;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createTime;
}
