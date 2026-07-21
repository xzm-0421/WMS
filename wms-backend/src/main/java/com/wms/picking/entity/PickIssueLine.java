package com.wms.picking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("pick_issue_line")
public class PickIssueLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String issueNo;
    private Integer lineNo;
    private String materialCode;
    private BigDecimal pickQty;
    private BigDecimal pickedQty;
    private String sourceLocation;
    private String batchNo;
}
