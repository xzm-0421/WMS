package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 委外领料单（SUB_PickMtrl）Save 请求，由 PDA 扫委外用料清单后提交组装。
 */
@Data
@Builder
public class KingdeeSubPickMtrlRequest {

    private String batchNo;
    /** 源委外用料清单单号 */
    private String sourceBillNo;
    private String sourceBillType;
    private Long sourceBillId;
    /** 供应商编码（必填） */
    private String supplierCode;
    /** 头仓库（可选） */
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
        private Long ppBomBillId;
    }
}
