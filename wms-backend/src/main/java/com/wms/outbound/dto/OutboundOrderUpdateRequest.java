package com.wms.outbound.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class OutboundOrderUpdateRequest {

    private String orderType;
    private String warehouseCode;
    private String productionOrderNo;
    private String customerCode;
    private LocalDate planDate;
    private String remark;
}
