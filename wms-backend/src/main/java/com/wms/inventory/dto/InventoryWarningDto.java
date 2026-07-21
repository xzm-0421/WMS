package com.wms.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryWarningDto {

    private String warehouseCode;
    private String materialCode;
    private String materialName;
    private BigDecimal currentQty;
    private BigDecimal safetyQty;
    private BigDecimal shortageQty;
}
