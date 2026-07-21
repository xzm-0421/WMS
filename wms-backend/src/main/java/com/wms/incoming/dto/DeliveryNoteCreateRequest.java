package com.wms.incoming.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DeliveryNoteCreateRequest {
    private String purchaseOrderNo;
    private String supplierCode;
    private String remark;
    private List<LineItem> lines;

    @Data
    public static class LineItem {
        private String materialCode;
        private BigDecimal actualQty;
    }
}
