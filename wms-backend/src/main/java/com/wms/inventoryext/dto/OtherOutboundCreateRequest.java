package com.wms.inventoryext.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OtherOutboundCreateRequest {
    private String outboundType;
    private String warehouseCode;
    private String targetDesc;
    private List<LineItem> lines;

    @Data
    public static class LineItem {
        private String materialCode;
        private String materialName;
        private BigDecimal quantity;
        private String locationCode;
        private String batchNo;
    }
}
