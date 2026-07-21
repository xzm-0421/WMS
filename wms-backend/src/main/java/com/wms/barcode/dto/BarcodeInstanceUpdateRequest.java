package com.wms.barcode.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BarcodeInstanceUpdateRequest {
    private String materialCode;
    private String batchNo;
    private String serialNo;
    private String packBarcode;
    private Integer status;
    private BigDecimal labelWidthMm;
    private BigDecimal labelHeightMm;
}
