package com.wms.inbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("inbound_order_detail")
public class InboundOrderDetail {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    private String unitCode;
    private BigDecimal orderQty;
    private BigDecimal receivedQty;
    private String batchNo;
    private LocalDate productionDate;
    private String targetLocation;
    private String qcStatus;
    private String lineStatus;
}
