package com.wms.barcode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BarcodeReprintResult {
    private String archiveNo;
    private String barcodeContent;
    private String barcodeType;
    private String labelFormat;
    private Integer reprintCount;
}
