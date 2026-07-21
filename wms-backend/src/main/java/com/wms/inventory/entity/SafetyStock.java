package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("safety_stock")
public class SafetyStock {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String warehouseCode;
    private String materialCode;
    private BigDecimal safetyQty;
    private BigDecimal maxQty;
    private Integer status;
}
