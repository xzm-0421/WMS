package com.wms.integration.kingdee.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class KingdeeReceiveBillVo {
    private String billNo;
    private LocalDate billDate;
    private String supplierCode;
    private String supplierName;
    private String documentStatus;
    private String warehouseCode;
    /** 金蝶单据内码 FID */
    private Long billId;
    /** 生产订单号（用料清单头等） */
    private String moBillNo;
    /** 产品编码（用料清单产品） */
    private String parentMaterialCode;
    private Integer totalLines;
    private Integer pendingLines;
    private List<KingdeeReceiveBillLineVo> lines;
}
