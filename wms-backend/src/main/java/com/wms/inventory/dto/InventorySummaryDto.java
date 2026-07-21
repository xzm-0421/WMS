package com.wms.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventorySummaryDto {

    private String warehouseCode;
    private String materialCode;
    private BigDecimal totalStockQty;
    private BigDecimal totalAvailableQty;
    private BigDecimal totalFrozenQty;
}
