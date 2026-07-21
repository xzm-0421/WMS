package com.wms.inbound.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class InboundOrderUpdateRequest {

    private String orderType;
    private String warehouseCode;
    private String supplierCode;
    private String productionOrderNo;
    private String sourceOrderNo;
    private LocalDate planDate;
    private String remark;
}
