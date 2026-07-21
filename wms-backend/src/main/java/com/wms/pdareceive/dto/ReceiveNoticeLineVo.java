package com.wms.pdareceive.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReceiveNoticeLineVo {
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    private String specification;
    private String batchNo;
    private String unitCode;
    /** 金蝶仓库编码 FStockId */
    private String erpStockCode;
    private BigDecimal planQty;
    private BigDecimal scannedQty;
    private BigDecimal submittedQty;
    private BigDecimal pendingSubmitQty;
    /** 剩余可领 = 计划 - 已提交 */
    private BigDecimal remainQty;
    private Boolean checked;
    private String scannedBarcode;
    private BigDecimal scannedBarcodeQty;
    private LocalDateTime lastScanTime;
}
