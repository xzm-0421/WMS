package com.wms.barcode.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BarcodeTraceResult {
    private String barcodeContent;
    private String materialCode;
    private String batchNo;
    private String serialNo;
    private String packBarcode;
    private String ruleCode;
    private Integer versionNo;
    private List<TraceLinkVo> links;

    @Data
    @Builder
    public static class TraceLinkVo {
        private String refType;
        private String refNo;
        private String transactionNo;
        private String materialCode;
        private String batchNo;
        private String serialNo;
        private String remark;
        private LocalDateTime createTime;
    }
}
