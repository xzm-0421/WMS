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

    /** 期初库存仓库编码 */
    private String warehouseCode;
    /** 期初库存仓库名称（可不传，按编码回填） */
    private String warehouseName;

    /** 业务组织编码 */
    private String orgCode;
    /** 业务组织名称 */
    private String orgName;

    @NotBlank
    private String materialCode;

    private String materialName;
    private String specification;
    private String batchNo;
    private String productionDate;
    private BigDecimal quantity;
    /** 入库单位 */
    private String unitCode;
    /** 计价单位 */
    private String priceUnitCode;

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
