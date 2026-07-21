package com.wms.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InventoryChangeCommand {

    private String transactionType;
    private String warehouseCode;
    private String locationCode;
    private String materialCode;
    private String batchNo;
    private BigDecimal quantity;
    private String sourceOrderType;
    private String sourceOrderNo;
    private Integer sourceOrderLine;
    private String operatorId;
    private String operatorName;
    private String deviceNo;
    private String remark;
}
