package com.wms.pdastockcount.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class StockCountListItemVo {
    private String billNo;
    private LocalDate billDate;
    private String warehouseCode;
    private String stockOrgCode;
    private String remark;
    private String documentStatus;
    private Integer totalLines;
    private Integer countedLines;
    /** COUNTING / COMPLETED / NEW */
    private String scanStatus;
    private boolean inProgress;
    /** 当前占用操作人（未过期） */
    private String lockUserName;
    private Boolean locked;
}
