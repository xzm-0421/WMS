package com.wms.integration.kingdee.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 金蝶物料盘点作业单据。
 */
@Data
@Builder
public class KingdeeStockCountBillVo {

    private String billNo;
    private Long billId;
    private LocalDate billDate;
    private String documentStatus;
    private String stockOrgCode;
    private String warehouseCode;
    private String remark;
    private Integer totalLines;
    private List<KingdeeStockCountLineVo> lines;
}
