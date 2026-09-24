package com.wms.mobile.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 移动端离线报工批量补传请求。
 */
@Data
public class MobileMesSyncRequest {

    private String deviceNo;
    private List<Item> items;

    @Data
    public static class Item {
        private String clientId;
        private String clientReportNo;
        private Long clientTime;
        private String moNo;
        private String processCode;
        private String reportType;
        private BigDecimal qty;
        private BigDecimal weightKg;
        private String equipmentCode;
        private String defectNo;
        private String remark;
    }
}
