package com.wms.stockcheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stockcheck_diff")
public class StockcheckDiff {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String materialCode;
    private String locationCode;
    private String batchNo;
    private BigDecimal diffQty;
    private String diffReason;
    private String status;
    private String approverId;
    private LocalDateTime approveTime;
    private LocalDateTime createTime;
}
