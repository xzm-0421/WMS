package com.wms.mes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_report")
public class MesReport extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportNo;
    private String moNo;
    private String processCode;
    private String processName;
    private Long planId;
    private String reportType;
    private BigDecimal qty;
    private BigDecimal weightKg;
    private String equipmentCode;
    private String equipmentName;
    private String operatorId;
    private String operatorName;
    private String remark;
    private String defectNo;
    private LocalDateTime reportTime;
    private String syncStatus;
    private LocalDateTime syncTime;
    private String erpBillNo;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String cancelReason;
    private String clientReportNo;
    private LocalDateTime clientTime;
}
