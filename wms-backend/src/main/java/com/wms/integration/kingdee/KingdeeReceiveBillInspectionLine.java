package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收料通知单明细分录上的来料检验数量字段（金蝶 PUR_ReceiveBill / FDetailEntity）。
 */
@Data
@Builder
public class KingdeeReceiveBillInspectionLine {
    private String billNo;
    private String supplierCode;
    private String supplierName;
    private String documentStatus;
    private LocalDate billDate;
    /** 来料检验标志 */
    private boolean checkIncoming;
    /** 收料数 FDetailEntity.FActReceiveQty */
    private BigDecimal receiveQty;
    /** 检验数量 FDetailEntity.FCheckBaseQty */
    private BigDecimal checkQty;
    /** 判退数量 FDetailEntity.FRefuseBaseQty */
    private BigDecimal refuseQty;
    /** 合格数量(基本单位) FDetailEntity.FReceiveBaseQty */
    private BigDecimal qualifiedQty;
    /** 样本破坏数量(基本单位) FDetailEntity.FSampleDamageBaseQty */
    private BigDecimal sampleDamageQty;
    /** 让步接收数量(基本单位) FDetailEntity.FCsnReceiveBaseQty */
    private BigDecimal concessionQty;
    /** 工废数量(基本单位) FDetailEntity.FProcScrapBaseQty */
    private BigDecimal procScrapQty;
    /** 料废数量(基本单位) FDetailEntity.FMtrlScrapBaseQty */
    private BigDecimal mtrlScrapQty;
    /** 单据物料明细行数 */
    private Integer materialLineCount;
}
