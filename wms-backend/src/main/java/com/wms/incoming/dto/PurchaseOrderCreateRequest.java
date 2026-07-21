package com.wms.incoming.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseOrderCreateRequest {
    private String supplierCode;
    private String warehouseCode;
    private LocalDate planArriveDate;
    private String remark;
    private List<LineItem> lines;

    @Data
    public static class LineItem {
        private String materialCode;
        private String materialName;
        private String unitCode;
        private BigDecimal orderQty;
        private String batchNo;
    }
}
