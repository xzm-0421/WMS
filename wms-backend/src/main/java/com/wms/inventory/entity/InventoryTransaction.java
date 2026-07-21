package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_transaction")
public class InventoryTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String transactionNo;
    private String transactionType;
    private String warehouseCode;
    private String locationCode;
    private String materialCode;
    private String batchNo;
    private BigDecimal transactionQty;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private String sourceOrderType;
    private String sourceOrderNo;
    private Integer sourceOrderLine;
    private String operatorId;
    private String operatorName;
    private String deviceNo;
    private LocalDateTime operationTime;
    private String remark;
    private LocalDateTime createTime;
}
