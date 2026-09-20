package com.wms.pdareceive.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ReceiveNoticeDetailVo {
    private String billNo;
    private String billType;
    private String direction;
    private String billTypeLabel;
    private LocalDate billDate;
    private String supplierCode;
    private String supplierName;
    private String warehouseCode;
    /** 金蝶默认仓库（由明细或 WMS 仓库映射得出） */
    private String erpWarehouseCode;
    private String scanStatus;
    /** 金蝶建单人姓名 */
    private String creatorName;
    private String creatorKdUserNumber;
    /** 是否需要占用单据锁 */
    private Boolean lockRequired;
    /** 最近一次提交对应的金蝶采购入库单号 */
    private String erpBillNo;
    /** 最近一次金蝶同步状态 */
    private String erpSyncStatus;
    private Integer totalLines;
    private Integer checkedLines;
    private Integer submittedLines;
    private List<ReceiveNoticeLineVo> lines;
}
