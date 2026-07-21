package com.wms.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class InboundBatchScanRequest {

    private List<InboundScanItem> items;

    @NotBlank
    private String deviceNo;

    @Data
    public static class InboundScanItem {
        @NotNull
        private Integer lineNo;
        @NotBlank
        private String materialCode;
        private String batchNo;
        @NotNull
        private BigDecimal quantity;
        @NotBlank
        private String targetLocation;
        private String barcodeContent;
    }
}
