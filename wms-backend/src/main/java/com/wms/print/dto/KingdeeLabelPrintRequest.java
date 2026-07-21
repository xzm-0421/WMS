package com.wms.print.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 金蝶云星空企业版发起的物料标签打印请求
 */
@Data
public class KingdeeLabelPrintRequest {

    /** 金蝶源单号（收料通知单/采购单等） */
    private String sourceBillNo;

    @NotBlank
    private String materialCode;

    private String materialName;
    private String specification;
    private String batchNo;
    private String productionDate;
    private BigDecimal quantity;
    private String unitCode;

    /** 条码内容，默认物料编码+批次 */
    private String barcodeContent;

    /** QR / CODE128 */
    private String barcodeType;

    private BigDecimal labelWidthMm;
    private BigDecimal labelHeightMm;
    private Integer copies;

    private String operatorName;
    private String remark;
}
