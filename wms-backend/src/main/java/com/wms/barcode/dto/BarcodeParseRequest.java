package com.wms.barcode.dto;

import lombok.Data;

@Data
public class BarcodeParseRequest {

    private String ruleCode;
    private String barcodeContent;
}
