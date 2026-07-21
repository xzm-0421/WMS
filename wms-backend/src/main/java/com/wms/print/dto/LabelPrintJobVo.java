package com.wms.print.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LabelPrintJobVo {
    private Long id;
    private String jobId;
    private String sourceType;
    private String sourceBillNo;
    private String materialCode;
    private String materialName;
    private String specification;
    private String batchNo;
    private String productionDate;
    private BigDecimal quantity;
    private String unitCode;
    private String barcodeContent;
    private String barcodeType;
    private BigDecimal labelWidthMm;
    private BigDecimal labelHeightMm;
    private Integer copies;
    private String status;
    private String operatorId;
    private String operatorName;
    private String errorMessage;
    private String printUrl;
    private LocalDateTime createTime;
    private LocalDateTime openedTime;
    private LocalDateTime printedTime;
}
