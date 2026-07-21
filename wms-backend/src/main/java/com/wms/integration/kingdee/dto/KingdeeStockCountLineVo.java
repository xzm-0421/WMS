package com.wms.integration.kingdee.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 金蝶物料盘点作业分录。
 */
@Data
@Builder
public class KingdeeStockCountLineVo {

    private Integer lineNo;
    /** 金蝶分录内码 */
    private Long entryId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private String unitCode;
    /** 账存数量（应有数量） */
    private BigDecimal bookQty;
    /** 金蝶已录盘点数量 */
    private BigDecimal countQty;
}
