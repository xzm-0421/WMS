package com.wms.barcode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BarcodeGenerateResult {
    private String barcodeContent;
    private String ruleCode;
    private Integer versionNo;
    private String materialCode;
    private String batchNo;
    private String serialNo;
    private String packBarcode;
    private String barcodeType;
    private Long instanceId;
    private String archiveNo;
}
