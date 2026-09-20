package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 发货确认后，补全销售出库分录的批号/仓库（及实发数量）。
 */
@Data
@Builder
public class KingdeeSalOutStockEntryFill {

    /** 发货通知分录内码（下推 EntryIds / 匹配源单） */
    private Long sourceEntryId;

    private String materialCode;

    private Integer lineNo;

    /** 批号 FLot.FNumber */
    private String lotNumber;

    /** 仓库 FStockId.FNumber（与金蝶 SAL_OUTSTOCK 一致，勿用 FStockID） */
    private String stockNumber;

    /** 实发数量；可空则仅补批号/仓库 */
    private BigDecimal realQty;
}
