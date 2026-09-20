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
@TableName("mes_transfer")
public class MesTransfer extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String transferNo;
    private String moNo;
    private String fromProcessCode;
    private String fromProcessName;
    private String toProcessCode;
    private String toProcessName;
    private BigDecimal qty;
    private Integer autoFlag;
    private String operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime transferTime;
    private String syncStatus;
    private LocalDateTime syncTime;
    private String erpBillNo;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String cancelReason;
}
