package com.wms.print.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("label_print_job")
public class LabelPrintJob {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobId;
    /** KINGDEE / MANUAL */
    private String sourceType;
    private String sourceBillNo;
    /** 期初库存打印关联仓库 */
    private String warehouseCode;
    private String warehouseName;
    /** 业务组织编码/名称 */
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
    /** 入库单位（库存单位） */
    private String unitCode;
    /** 计价单位 */
    private String priceUnitCode;
    private String barcodeContent;
    /** QR / CODE128 */
    private String barcodeType;
    private BigDecimal labelWidthMm;
    private BigDecimal labelHeightMm;
    private Integer copies;
    /** PENDING / OPENED / PRINTED / FAILED */
    private String status;
    private String operatorId;
    private String operatorName;
    private String requestPayload;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime openedTime;
    private LocalDateTime printedTime;
}
