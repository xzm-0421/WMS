package com.wms.barcode.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class BarcodeGenerateRequest {
    private String ruleCode;
    private String materialCode;
    private String batchNo;
    private String packBarcode;
    private String serialNo;
    /** 是否自动分配全局唯一序列号 */
    private Boolean autoSerial;
    private String refType;
    private String refNo;
    private Map<String, String> extraFields;
    /** 标签宽度(mm) */
    private BigDecimal labelWidthMm;
    /** 标签高度(mm) */
    private BigDecimal labelHeightMm;
    /** 套打业务类型 */
    private String labelFormat;
}
