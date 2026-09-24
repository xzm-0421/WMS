package com.wms.quality.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QcJudgeRequest {

    private BigDecimal qualifiedQty;
    private BigDecimal unqualifiedQty;
    /** QUALIFIED / UNQUALIFIED / CONCESSION */
    private String judgeResult;
    private String judgeRemark;
    private String judgeBy;
}
