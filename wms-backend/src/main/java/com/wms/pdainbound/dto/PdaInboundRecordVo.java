package com.wms.pdainbound.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PdaInboundRecordVo {

    private Long id;
    private String recordNo;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unitCode;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private BigDecimal quantity;
    private String barcodeContent;
    private String operatorId;
    private String operatorName;
    private String deviceNo;
    private String status;
    private String erpSyncStatus;
    private LocalDateTime erpSyncTime;
    private String erpBillNo;
    private String erpSyncMessage;
    private Integer erpRetryCount;
    private String auditorId;
    private String auditorName;
    private LocalDateTime auditTime;
    private String reverseBy;
    private LocalDateTime reverseTime;
    private String reverseReason;
    private String remark;
    private String sourceType;
    private String sourceBillNo;
    private Integer sourceLineNo;
    private String submitBatchNo;
    private String supplierCode;
    private String erpStockCode;
    private Long sourceBillId;
    private Long sourceEntryId;
    private LocalDateTime createTime;
}
