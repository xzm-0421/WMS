package com.wms.barcode.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BarcodeArchiveVo {
    private Long id;
    private String archiveNo;
    private String barcodeContent;
    private Long barcodeInstanceId;
    private String ruleCode;
    private Integer versionNo;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private String serialNo;
    private String packBarcode;
    private String barcodeType;
    private String templateCode;
    private String labelFormat;
    private BigDecimal labelWidthMm;
    private BigDecimal labelHeightMm;
    private String printParamsJson;
    private String actionType;
    private Integer reprintCount;
    private LocalDateTime lastReprintTime;
    private LocalDateTime createTime;
}
