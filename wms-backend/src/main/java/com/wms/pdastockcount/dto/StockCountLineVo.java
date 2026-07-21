package com.wms.pdastockcount.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StockCountLineVo {
    private Integer lineNo;
    private Long entryId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private String unitCode;
    /** 应有数量（账存） */
    private BigDecimal bookQty;
    /** 实盘数量 */
    private BigDecimal actualQty;
    /** 差异 = 实盘 - 应有 */
    private BigDecimal diffQty;
    /** PENDING / COUNTED */
    private String lineStatus;
    private boolean counted;
}
