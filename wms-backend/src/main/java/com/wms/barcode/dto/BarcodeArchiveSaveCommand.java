package com.wms.barcode.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class BarcodeArchiveSaveCommand {
    private String barcodeContent;
    private Long barcodeInstanceId;
    private String ruleCode;
    private Integer versionNo;
    private String materialCode;
    private String batchNo;
    private String serialNo;
    private String packBarcode;
    private String barcodeType;
    private String templateCode;
    private String separator;
    private String segmentsJson;
    private String labelFormat;
    private BigDecimal labelWidthMm;
    private BigDecimal labelHeightMm;
    private String actionType;
    private Map<String, String> extraFields;
}
