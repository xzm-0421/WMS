package com.wms.picking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("prep_notice_line")
public class PrepNoticeLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String noticeNo;
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    private BigDecimal demandQty;
    private BigDecimal pickedQty;
    private String recommendLoc;
}
