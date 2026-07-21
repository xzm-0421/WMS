package com.wms.barcode.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BarcodeRecognizeResult {
    private String raw;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unitCode;
    private String batchNo;
    private String serialNo;
    private String packBarcode;
    private String locationCode;
    private String ruleCode;
    private Integer versionNo;
    private Long instanceId;
    /** INSTANCE / RULE / HEURISTIC / LOCATION / LABEL_JOB */
    private String parseMode;
    /** 二维码解析出的领料数量（可为空，由业务层兜底） */
    private BigDecimal quantity;
    /** JSON / PIPE / SUFFIX / RULE / LABEL_JOB */
    private String quantitySource;
}
