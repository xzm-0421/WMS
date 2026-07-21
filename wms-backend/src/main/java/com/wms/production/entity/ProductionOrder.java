package com.wms.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("production_order")
public class ProductionOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String productCode;
    private String productName;
    private BigDecimal planQty;
    private BigDecimal completedQty;
    private LocalDate planStart;
    private LocalDate planEnd;
    private String status;
    private String warehouseCode;
    private String remark;
    private LocalDateTime createTime;
    private Integer deleted;
}
