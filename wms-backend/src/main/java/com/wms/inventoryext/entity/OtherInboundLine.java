package com.wms.inventoryext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("other_inbound_line")
public class OtherInboundLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String materialCode;
    private String materialName;
    private BigDecimal quantity;
    private String locationCode;
    private String batchNo;
}
