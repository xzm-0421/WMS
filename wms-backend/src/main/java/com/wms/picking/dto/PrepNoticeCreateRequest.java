package com.wms.picking.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PrepNoticeCreateRequest {
    private String productionPlanNo;
    private String warehouseCode;
    private LocalDateTime demandTime;
    private List<LineItem> lines;

    @Data
    public static class LineItem {
        private String materialCode;
        private String materialName;
        private BigDecimal demandQty;
    }
}
