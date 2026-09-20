package com.wms.mes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_op_plan")
public class MesOpPlan extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String planKey;
    private String erpBillNo;
    private Long erpEntryId;
    private String moNo;
    private String productCode;
    private String productName;
    private String processCode;
    private String processName;
    private Integer seqNo;
    private BigDecimal planQty;
    private BigDecimal reportedQty;
    private BigDecimal reworkReportedQty;
    private BigDecimal overReceiveRatio;
    private LocalDate planStart;
    private LocalDate planEnd;
    private String erpStatus;
    private String planStatus;
    private String syncStatus;
    private LocalDateTime lastSyncTime;
    private String failReason;
    private String changeRejectReason;
    private String workShopCode;
    private String workShopName;
}
