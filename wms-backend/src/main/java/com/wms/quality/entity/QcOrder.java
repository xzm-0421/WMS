package com.wms.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("qc_order")
public class QcOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String qcNo;
    private String sourceType;
    private String sourceNo;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private String qcType;
    private BigDecimal sampleQty;
    private BigDecimal qcQty;
    private BigDecimal qualifiedQty;
    private BigDecimal unqualifiedQty;
    private String status;
    private String result;
    private String inspectorId;
    private String inspectorName;
    private LocalDateTime inspectTime;
    private String judgeResult;
    private String judgeRemark;
    private LocalDateTime judgeTime;
    private String judgeBy;
    private String concessionReason;
    private String concessionApprover;
    private LocalDateTime concessionTime;
    private String remark;
    private LocalDateTime createTime;
}
