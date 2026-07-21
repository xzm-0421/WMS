package com.wms.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("bom_detail")
public class BomDetail {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bomCode;
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    private String unitCode;
    private BigDecimal qtyPer;
}
