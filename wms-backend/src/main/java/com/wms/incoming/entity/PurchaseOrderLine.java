package com.wms.incoming.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_order_line")
public class PurchaseOrderLine {

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
    private String lineStatus;
}
