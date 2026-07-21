package com.wms.incoming.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("delivery_note_line")
public class DeliveryNoteLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deliveryNo;
    private Integer lineNo;
    private String materialCode;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    private BigDecimal diffQty;
}
