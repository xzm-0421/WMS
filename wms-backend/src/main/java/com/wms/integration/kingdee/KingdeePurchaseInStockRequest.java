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
    /** 业务类型 CG/WW，空则用配置默认 */
    private String businessType;
    /** 入库单单据类型编码，空则按业务类型选配置默认 */
    private String billTypeNumber;
    private String supplierCode;
    private LocalDate billDate;
    private List<Line> lines;

    @Data
    @Builder
    public static class Line {
        private String materialCode;
        private String materialName;
        private String unitCode;
        /** 计价单位，空则与 unitCode 相同 */
        private String priceUnitCode;
        private String warehouseCode;
        private String locationCode;
        private String batchNo;
        private BigDecimal quantity;
        /** 计价数量，空则与 quantity 相同 */
        private BigDecimal priceUnitQty;
        private Integer sourceLineNo;
        /** 金蝶源单内码 FID */
        private Long sourceBillId;
        /** 收料分录内码 FEntryID */
        private Long sourceEntryId;
        /** 收料通知单关联携带量（FInStockEntry_Link） */
        private KingdeeInStockEntryLink sourceLink;
        /**
         * 送货单号：来自收料通知单 {@code F_QVHU_Text_qtr}，
         * 写入采购入库同名字段 {@link KingdeePurchaseInStockBuilder#SEND_BILL_NO_FIELD}。
         */
        private String sendBillNo;
        /** 采购订单单号 FPOOrderNo */
        private String poOrderNo;
        /** 采购订单分录内码 FPOORDERENTRYID */
        private Long poOrderEntryId;
    }
}
