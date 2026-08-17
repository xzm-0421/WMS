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
    /** 计价/辅助单位（双单位时展示） */
    private String auxUnitCode;
    /** 是否启用双单位编辑 */
    private Boolean multiUnit;
    /**
     * 录入侧是否对应计价单位（aux）。
     * 主录非重量单位（通常 PCS），自动换算到 KG（重量侧，可能是库存或计价）。
     */
    private Boolean inputMapsToAux;
    /** 主录入单位（件数侧） */
    private String inputUnitCode;
    /** 自动换算单位（KG 侧） */
    private String autoUnitCode;
    /** 金蝶仓库编码 FStockId */
    private String erpStockCode;
    private BigDecimal planQty;
    private BigDecimal scannedQty;
    private BigDecimal submittedQty;
    private BigDecimal pendingSubmitQty;
    /** 剩余可领 = 计划 - 已提交 */
    private BigDecimal remainQty;
    private BigDecimal planAuxQty;
    private BigDecimal scannedAuxQty;
    private BigDecimal submittedAuxQty;
    private BigDecimal pendingSubmitAuxQty;
    private BigDecimal remainAuxQty;
    private Boolean checked;
    private String scannedBarcode;
    private BigDecimal scannedBarcodeQty;
    private LocalDateTime lastScanTime;
}
