package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购入库单（STK_InStock）直接 Save 请求，由收料通知单 PDA 提交批次组装。
 */
@Data
@Builder
public class KingdeePurchaseInStockRequest {

    /** WMS 批次号，仅用于日志 */
    private String batchNo;
    /** 金蝶收料通知单号 */
    private String sourceBillNo;
    @Builder.Default
    private String sourceBillType = "PUR_ReceiveBill";
    private String supplierCode;
    private LocalDate billDate;
    private List<Line> lines;

    @Data
    @Builder
    public static class Line {
        private String materialCode;
        private String materialName;
        private String unitCode;
        private String warehouseCode;
        private String locationCode;
        private String batchNo;
        private BigDecimal quantity;
        private Integer sourceLineNo;
        /** 金蝶源单内码 FID */
        private Long sourceBillId;
        /** 收料分录内码 FEntryID */
        private Long sourceEntryId;
        /** 收料通知单关联携带量（FInStockEntry_Link） */
        private KingdeeInStockEntryLink sourceLink;
        /** 采购订单单号 FPOOrderNo */
        private String poOrderNo;
        /** 采购订单分录内码 FPOORDERENTRYID */
        private Long poOrderEntryId;
    }
}
