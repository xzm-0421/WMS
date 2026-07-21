package com.wms.inbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inbound_order")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String orderType;
    private String warehouseCode;
    private String supplierCode;
    private String productionOrderNo;
    private String sourceOrderNo;
    private LocalDate planDate;
    private LocalDateTime actualDate;
    private String status;
    private String creatorId;
    private String creatorName;
    private String auditorId;
    private String auditorName;
    private LocalDateTime auditTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
