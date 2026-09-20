package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 其他出库单（STK_MisDelivery）Save 请求。
 */
@Data
@Builder
public class KingdeeMisDeliveryRequest {

    private String batchNo;
    private String billNo;
    private LocalDate billDate;
    private String customerCode;
    private String deptCode;
    private String note;
    private String stockDirect;
    private String bizType;
    private String supplierCode;
    private List<Line> lines;

    @Data
    @Builder
    public static class Line {
        private String materialCode;
        private String unitCode;
        private String warehouseCode;
        private String locationCode;
        private String batchNo;
        private BigDecimal quantity;
        private String entryNote;
        private String ownerTypeId;
        private String ownerId;
        private String keeperTypeId;
        private String keeperId;
        private String stockStatusNumber;
    }
}
