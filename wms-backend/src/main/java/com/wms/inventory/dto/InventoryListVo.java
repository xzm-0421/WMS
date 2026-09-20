package com.wms.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 实时库存列表展示（含物料主数据名称/规格/单位）。
 */
@Data
public class InventoryListVo {

    private Long id;
    private String materialCode;
    /** 标签号（对应库存批次号 batch_no） */
    private String labelNo;
    private String materialName;
    private String specification;
    private BigDecimal availableQty;
    private String unitCode;
    private String warehouseCode;
    private LocalDate productionDate;
    private LocalDateTime createTime;
    private BigDecimal stockQty;
    private String locationCode;
    private String batchNo;
    private BigDecimal frozenQty;
    private String stockStatus;
}
