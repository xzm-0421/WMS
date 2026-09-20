package com.wms.pdareceive.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReceiveNoticeListItemVo {
    private String billNo;
    private String billType;
    private String direction;
    private String billTypeLabel;
    private LocalDate billDate;
    private String supplierCode;
    private String supplierName;
    private String warehouseCode;
    private String scanStatus;
    /** 金蝶建单人姓名 */
    private String creatorName;
    /** 最近一次提交对应的金蝶采购入库单号 */
    private String erpBillNo;
    /** 最近一次金蝶同步状态 */
    private String erpSyncStatus;
    private Integer totalLines;
    private Integer checkedLines;
    private Integer submittedLines;
    private Integer pendingLines;
    private Boolean inProgress;
    /** 当前占用操作人（未过期） */
    private String lockUserName;
    private Boolean locked;
}
