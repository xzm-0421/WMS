package com.wms.pdainbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pda_inbound_record")
public class PdaInboundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String recordNo;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unitCode;
    /** 计价/辅助单位 */
    private String auxUnitCode;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private BigDecimal quantity;
    /** 计价单位数量 */
    private BigDecimal auxQuantity;
    private String barcodeContent;
    private String operatorId;
    private String operatorName;
    private String deviceNo;
    /** SUBMITTED / AUDITED / REVERSED */
    private String status;
    /** PENDING / SYNCING / SUCCESS / FAILED */
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
    /** 金蝶供应商编码，来自收料通知单 */
    private String supplierCode;
    /** 金蝶仓库编码 FStockId */
    private String erpStockCode;
    /** 金蝶收料通知单内码 FID */
    private Long sourceBillId;
    /** 金蝶收料分录内码 FEntryID */
    private Long sourceEntryId;
    /** 源单剩余可入库数量(基本单位) FRemainInStockBaseQtyOld */
    private BigDecimal sourceRemainInStockBaseQtyOld;
    /** 源单基本单位数量 FBaseUnitQtyOld */
    private BigDecimal sourceBaseUnitQtyOld;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
