package com.wms.picking.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WorkshopReturnCreateRequest {
    private String issueNo;
    private String warehouseCode;
    private String returnReason;
    private List<LineItem> lines;

    @Data
    public static class LineItem {
        private String materialCode;
        private BigDecimal returnQty;
        private String batchNo;
        private String targetLocation;
    }
}
