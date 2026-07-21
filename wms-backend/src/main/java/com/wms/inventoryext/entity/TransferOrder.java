package com.wms.inventoryext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("transfer_order")
public class TransferOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String transferNo;
    private String transferType;
    private String sourceWarehouse;
    private String sourceLocation;
    private String targetWarehouse;
    private String targetLocation;
    private String materialCode;
    private String batchNo;
    private BigDecimal transferQty;
    private String status;
    private String operatorId;
    private LocalDateTime operationTime;
    private String creatorName;
    private String remark;
    private LocalDateTime createTime;
    private Integer deleted;
}
