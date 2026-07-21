package com.wms.incoming.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_return_line")
public class PurchaseReturnLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String returnNo;
    private Integer lineNo;
    private String materialCode;
    private BigDecimal returnQty;
    private String batchNo;
}
