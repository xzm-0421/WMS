package com.wms.quality.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QcOrderCreateRequest {

    private String sourceType;
    private String sourceNo;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private String qcType;
    private BigDecimal qcQty;
}
