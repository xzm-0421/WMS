package com.wms.picking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("workshop_return_line")
public class WorkshopReturnLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String returnNo;
    private Integer lineNo;
    private String materialCode;
    private BigDecimal returnQty;
    private String targetLocation;
    private String batchNo;
}
