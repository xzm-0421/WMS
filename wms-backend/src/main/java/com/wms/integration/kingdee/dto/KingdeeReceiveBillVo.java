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
    /** 业务类型：CG 标准采购 / WW 委外 */
    private String businessType;
    /** 单据类型编码 FBillTypeID.FNumber */
    private String billTypeNumber;
    /** 送货单号 F_QVHU_Text_qtr（表头；分录未填时回退） */
    private String sendBillNo;
    /** 生产订单号（用料清单头等） */
    private String moBillNo;
    /** 产品编码（用料清单产品） */
    private String parentMaterialCode;
    /** 金蝶建单人编码 FCreatorId.FNumber */
    private String creatorKdUserNumber;
    /** 金蝶建单人姓名 */
    private String creatorName;
    private Integer totalLines;
    private Integer pendingLines;
    private List<KingdeeReceiveBillLineVo> lines;
}
