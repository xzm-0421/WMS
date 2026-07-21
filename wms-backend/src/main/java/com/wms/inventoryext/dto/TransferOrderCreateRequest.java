package com.wms.inventoryext.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferOrderCreateRequest {
    private String transferType;
    private String sourceWarehouse;
    private String sourceLocation;
    private String targetWarehouse;
    private String targetLocation;
    private String materialCode;
    private String batchNo;
    private BigDecimal transferQty;
    private String remark;
}
