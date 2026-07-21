package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 生产领料单（PRD_PickMtrl）Save 请求，由 PDA 扫生产用料清单后提交组装。
 */
@Data
@Builder
public class KingdeePickMtrlRequest {

    /** WMS 提交批次号 */
    private String batchNo;
    /** 源用料清单单号 */
    private String sourceBillNo;
    /** 源单类型编码 */
    private String sourceBillType;
    /** 源用料清单内码 FID */
    private Long sourceBillId;
    /** 车间编码 */
    private String workShopCode;
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
        /** 金蝶仓库编码 FStockId */
        private String warehouseCode;
        private String locationCode;
        private String batchNo;
        private BigDecimal quantity;
        private Integer sourceLineNo;
        private String entryNote;
        /** 产品编码 FParentMaterialId */
        private String parentMaterialCode;

        private String moBillNo;
        private Long moId;
        private Long moEntryId;
        private Integer moEntrySeq;

        private String ppBomBillNo;
        private Long ppBomEntryId;
        /** 用料清单头内码（Link / 源单内码） */
        private Long ppBomBillId;
    }
}
