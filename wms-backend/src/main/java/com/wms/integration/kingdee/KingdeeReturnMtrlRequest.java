package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 生产退料单（PRD_ReturnMtrl）Save 请求，由 PDA 扫生产领料单后提交组装。
 */
@Data
@Builder
public class KingdeeReturnMtrlRequest {

    private String batchNo;
    /** 源生产领料单号 */
    private String sourceBillNo;
    private String sourceBillType;
    /** 源领料单内码 */
    private Long sourceBillId;
    private String workShopCode;
    private String stockCode;
    private LocalDate billDate;
    private String note;
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
        private String entryNote;
        private String parentMaterialCode;

        private String moBillNo;
        private Long moId;
        private Long moEntryId;
        private Integer moEntrySeq;

        private String ppBomBillNo;
        private Long ppBomEntryId;

        /** 源领料分录内码 */
        private Long pickEntryId;
        private Long pickBillId;
    }
}
