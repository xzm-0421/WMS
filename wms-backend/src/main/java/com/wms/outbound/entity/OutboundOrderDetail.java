package com.wms.outbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("outbound_order_detail")
public class OutboundOrderDetail {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unitCode;
    private BigDecimal demandQty;
    private BigDecimal issuedQty;
    private String batchNo;
    private String sourceLocation;
    private String lineStatus;
    private String remark;
}
