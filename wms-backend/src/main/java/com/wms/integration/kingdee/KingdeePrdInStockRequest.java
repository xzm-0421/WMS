package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 生产入库单（PRD_INSTOCK）Save 请求。
 * 源单默认生产汇报单 PRD_MORPT（带 FEntity_Link 反写选单量）。
 */
@Data
@Builder
public class KingdeePrdInStockRequest {

    private String batchNo;
    private LocalDate billDate;
    private String note;
    private String workShopCode;
    private List<Line> lines;

    @Data
    @Builder
    public static class Line {
        private String materialCode;
        private String materialName;
        private String unitCode;
        private BigDecimal mustQty;
        private BigDecimal realQty;
        private String workShopCode;
        private String warehouseCode;
        private String locationCode;
        private String batchNo;
        private String moBillNo;
        private Long moId;
        private Long moEntryId;
        private Integer moEntrySeq;
        /** 源单（汇报）分录内码 */
        private Long srcEntryId;
        /** 源单（汇报）单据内码 */
        private Long srcInterId;
        private String srcBillNo;
        private Integer srcEntrySeq;
        /** 入库类型：1=合格品入库 */
        private String inStockType;
        private String productType;
        private Boolean backFlush;
        private Integer sourceLineNo;
        private String entryNote;
    }
}
