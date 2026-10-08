package com.wms.mobile.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MobileMesPrintRequest {
    private String reportNo;
    private String moNo;
    private String productCode;
    private String productName;
    private BigDecimal weightKg;
    private Integer qty;
}
