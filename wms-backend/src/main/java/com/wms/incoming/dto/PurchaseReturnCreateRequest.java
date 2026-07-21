package com.wms.incoming.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PurchaseReturnCreateRequest {
    private String receiptRefNo;
    private String supplierCode;
    private String reason;
    private List<LineItem> lines;

    @Data
    public static class LineItem {
        private String materialCode;
        private BigDecimal returnQty;
        private String batchNo;
    }
}
