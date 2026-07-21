package com.wms.outbound.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecommendLocationDto {

    private String warehouseCode;
    private String locationCode;
    private String materialCode;
    private String batchNo;
    private BigDecimal availableQty;
    private LocalDateTime inboundDate;
}
