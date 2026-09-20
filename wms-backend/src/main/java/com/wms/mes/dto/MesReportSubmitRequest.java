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
}
