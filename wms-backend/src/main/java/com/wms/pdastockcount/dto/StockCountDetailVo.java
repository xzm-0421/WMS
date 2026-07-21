package com.wms.pdastockcount.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class StockCountDetailVo {
    private String billNo;
    private LocalDate billDate;
    private String warehouseCode;
    private String stockOrgCode;
    private String remark;
    private String documentStatus;
    private String scanStatus;
    private Integer totalLines;
    private Integer countedLines;
    private List<StockCountLineVo> lines;
}
