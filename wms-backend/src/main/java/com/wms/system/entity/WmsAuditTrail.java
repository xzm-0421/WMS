package com.wms.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wms_audit_trail")
public class WmsAuditTrail {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizNo;
    private String action;
    private String operatorId;
    private String operatorName;
    private String detailJson;
    private LocalDateTime createTime;
}
