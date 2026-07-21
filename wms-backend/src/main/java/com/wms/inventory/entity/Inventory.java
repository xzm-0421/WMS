package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String warehouseCode;
    private String locationCode;
    private String materialCode;
    private String batchNo;
    private BigDecimal stockQty;
    private BigDecimal availableQty;
    private BigDecimal frozenQty;
    private BigDecimal inTransitQty;
    private LocalDateTime inboundDate;
    private LocalDate productionDate;
    private LocalDate expireDate;
    private String stockStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
