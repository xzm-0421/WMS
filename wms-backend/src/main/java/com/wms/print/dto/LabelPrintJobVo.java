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
    private String warehouseCode;
    private String warehouseName;
    private String orgCode;
    private String orgName;
    private String materialCode;
    private String materialName;
    private String specification;
    private String batchNo;
    private String productionDate;
    /** FACTORY 厂内标签 / INCOMING 来料标签 */
    private String labelFormat;
    /** 客户简称（厂内）或供应商简称（来料） */
    private String partnerName;
    /** 板号（厂内） */
    private String boardNo;
    /** 包装号（来料） */
    private String packageNo;
    private BigDecimal quantity;
    /** 入库单位 */
    private String unitCode;
    /** 计价单位 */
    private String priceUnitCode;
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
    /** 绝对地址，金蝶浏览器可直接打开（含 autoPrint=1） */
    private String absolutePrintUrl;
    private LocalDateTime createTime;
    private LocalDateTime openedTime;
    private LocalDateTime printedTime;
}
