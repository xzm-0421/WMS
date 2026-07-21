package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购入库单分录与收料通知单的 FInStockEntry_Link 关联数据。
 */
@Data
@Builder
public class KingdeeInStockEntryLink {

    private Long linkId;
    private String flowId;
    private Integer flowLineId;
    private String ruleId;
    private Integer sourceTableId;
    private String sourceTableName;
    private Long sourceBillId;
    private Long sourceEntryId;
    private BigDecimal remainInStockBaseQtyOld;
    private BigDecimal remainInStockBaseQty;
    private BigDecimal baseUnitQtyOld;
    private BigDecimal baseUnitQty;
    private String lnk1TrackerId;
    private String lnk1State;
    private BigDecimal lnk1Amount;
    private String lnkTrackerId;
    private String lnkState;
    private BigDecimal lnkAmount;
    private String lnk2TrackerId;
    private String lnk2State;
    private BigDecimal lnk2Amount;

    public static KingdeeInStockEntryLink fromReceiveLine(KingdeeCloudProperties props,
                                                          Long sourceBillId,
                                                          Long sourceEntryId,
                                                          BigDecimal submitQty,
                                                          BigDecimal remainInStockBaseQtyOld,
                                                          BigDecimal baseUnitQtyOld) {
        BigDecimal qty = submitQty == null ? BigDecimal.ZERO : submitQty;
        BigDecimal remainOld = remainInStockBaseQtyOld != null && remainInStockBaseQtyOld.compareTo(BigDecimal.ZERO) > 0
                ? remainInStockBaseQtyOld : qty;
        BigDecimal baseOld = baseUnitQtyOld != null && baseUnitQtyOld.compareTo(BigDecimal.ZERO) > 0
                ? baseUnitQtyOld : qty;
        return KingdeeInStockEntryLink.builder()
                .linkId(0L)
                .flowId(" ")
                .flowLineId(0)
                .ruleId(props.getStockInReceiveLinkRuleId())
                .sourceTableId(props.getStockInReceiveLinkSTableId())
                .sourceTableName(props.getStockInReceiveLinkSTableName())
                .sourceBillId(sourceBillId)
                .sourceEntryId(sourceEntryId)
                .remainInStockBaseQtyOld(remainOld)
                .remainInStockBaseQty(qty)
                .baseUnitQtyOld(baseOld)
                .baseUnitQty(qty)
                .lnk1TrackerId(" ")
                .lnk1State(" ")
                .lnk1Amount(BigDecimal.ZERO)
                .lnkTrackerId(" ")
                .lnkState(" ")
                .lnkAmount(BigDecimal.ZERO)
                .lnk2TrackerId(" ")
                .lnk2State(" ")
                .lnk2Amount(BigDecimal.ZERO)
                .build();
    }
}
