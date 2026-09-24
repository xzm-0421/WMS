package com.wms.mes.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MesReportSubmitRequest {
    private String moNo;
    private String processCode;
    private String reportType;
    private BigDecimal qty;
    private BigDecimal weightKg;
    private String equipmentCode;
    private String remark;
    private String defectNo;
    /** 移动端离线幂等键 */
    private String clientReportNo;
    /** 客户端报工时间（epoch 毫秒） */
    private Long clientTime;
}
