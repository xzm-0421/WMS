package com.wms.stockcheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("stockcheck_detail")
public class StockcheckDetail {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Integer lineNo;
    private String materialCode;
    private String locationCode;
    private String batchNo;
    private BigDecimal bookQty;
    private BigDecimal actualQty;
    private BigDecimal diffQty;
    private String lineStatus;
}
