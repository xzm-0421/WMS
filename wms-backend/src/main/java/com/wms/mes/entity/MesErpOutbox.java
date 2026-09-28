package com.wms.mes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 不良单 / 返工工单回传 ERP 的出站队列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_erp_outbox")
public class MesErpOutbox extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizNo;
    private String action;
    private String syncStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String erpBillNo;
    private Long erpBillId;
    private String failReason;
    private LocalDateTime lastSyncTime;
}
