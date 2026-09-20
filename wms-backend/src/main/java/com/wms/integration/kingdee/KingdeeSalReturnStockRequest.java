package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售退货单（SAL_RETURNSTOCK）Save 请求：由已审核退货通知单扫码提交组装。
 */
@Data
@Builder
public class KingdeeSalReturnStockRequest {

    private String batchNo;
    private String sourceBillNo;
    private Long sourceBillId;
    private String customerCode;
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
        private Long sourceEntryId;
        private String returnTypeNumber;
        private LocalDate deliveryDate;
        private String entryNote;
    }
}
