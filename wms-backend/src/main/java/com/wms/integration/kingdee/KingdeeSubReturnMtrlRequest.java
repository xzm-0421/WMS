package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 委外退料单（SUB_RETURNMTRL）Save 请求，由 PDA 扫委外领料单后提交组装。
 */
@Data
@Builder
public class KingdeeSubReturnMtrlRequest {

    private String batchNo;
    /** 源委外领料单号 */
    private String sourceBillNo;
    private String sourceBillType;
    /** 源领料单内码 */
    private Long sourceBillId;
    /** 供应商编码（必填） */
    private String supplierCode;
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

        private String subReqBillNo;
        private Long subReqId;
        private Long subReqEntryId;
        private Integer subReqEntrySeq;

        private String ppBomBillNo;
        private Long ppBomEntryId;

        /** 源委外领料分录内码 */
        private Long pickEntryId;
        private Long pickBillId;
    }
}
