package com.wms.noticebill;

import com.wms.common.exception.BusinessException;
import com.wms.common.constant.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * WMS 通知单类型（入库/出库统一扫码模式）。
 */
public enum NoticeBillType {
    PURCHASE_RECEIVE(
            NoticeBillDirection.INBOUND,
            "收料通知单",
            "PUR_ReceiveBill",
            "PDA_RECEIVE_INBOUND",
            "RECEIVE_NOTICE",
            true,
            ""),
    PRODUCTION_IN(
            NoticeBillDirection.INBOUND,
            "生产汇报入库",
            "PRD_MORPT",
            "PRODUCTION_IN",
            "PRODUCTION_IN",
            false,
            ""),
    PRODUCTION_RETURN(
            NoticeBillDirection.INBOUND,
            "生产退料单",
            "PRD_ReturnMtrl",
            "PRODUCTION_RETURN",
            "PRODUCTION_RETURN",
            false,
            ""),
    OUTSOURCE_RETURN(
            NoticeBillDirection.INBOUND,
            "委外退料单",
            "SUB_RETURNMTRL",
            "OUTSOURCE_RETURN",
            "OUTSOURCE_RETURN",
            false,
            ""),
    OTHER_IN(
            NoticeBillDirection.INBOUND,
            "其他入库单",
            "STK_MISCELLANEOUS",
            "OTHER_IN",
            "OTHER_IN",
            false,
            ""),
    SALES_RETURN(
            NoticeBillDirection.INBOUND,
            "销售退货通知单",
            "SAL_RETURNNOTICE",
            "SALES_RETURN",
            "SALES_RETURN",
            false,
            ""),

    SALES_DELIVERY(
            NoticeBillDirection.OUTBOUND,
            "销售发货通知单",
            "SAL_DELIVERYNOTICE",
            "SALES_OUT",
            "SALES_DELIVERY",
            false,
            ""),
    PRODUCTION_ISSUE(
            NoticeBillDirection.OUTBOUND,
            "生产领料单",
            "PRD_PickMtrl",
            "PRODUCTION_OUT",
            "PRODUCTION_ISSUE",
            false,
            ""),
    PRODUCTION_FEED(
            NoticeBillDirection.OUTBOUND,
            "生产补料单",
            "PRD_FeedMtrl",
            "PRODUCTION_FEED",
            "PRODUCTION_FEED",
            false,
            ""),
    PRODUCTION_RET_STOCK(
            NoticeBillDirection.OUTBOUND,
            "生产退库单",
            "PRD_RetStock",
            "PRODUCTION_RET_STOCK",
            "PRODUCTION_RET_STOCK",
            false,
            ""),
    OUTSOURCE_ISSUE(
            NoticeBillDirection.OUTBOUND,
            "委外领料单",
            "SUB_PickMtrl",
            "OUTSOURCE_OUT",
            "OUTSOURCE_ISSUE",
            false,
            ""),
    OUTSOURCE_FEED(
            NoticeBillDirection.OUTBOUND,
            "委外补料单",
            "SUB_FEEDMTRL",
            "OUTSOURCE_FEED",
            "OUTSOURCE_FEED",
            false,
            ""),
    OTHER_OUT(
            NoticeBillDirection.OUTBOUND,
            "其他出库单",
            "STK_MisDelivery",
            "OTHER_OUT",
            "OTHER_OUT",
            false,
            ""),
    PURCHASE_RETURN(
            NoticeBillDirection.OUTBOUND,
            "采购退料单",
            "PUR_MRB",
            "PURCHASE_RETURN",
            "PURCHASE_RETURN",
            false,
            "");

    private final NoticeBillDirection direction;
    private final String label;
    private final String defaultFormId;
    private final String transactionType;
    private final String sourceOrderType;
    private final boolean useInspectionFilter;
    private final String extraFilter;

    NoticeBillType(NoticeBillDirection direction, String label, String defaultFormId,
                   String transactionType, String sourceOrderType,
                   boolean useInspectionFilter, String extraFilter) {
        this.direction = direction;
        this.label = label;
        this.defaultFormId = defaultFormId;
        this.transactionType = transactionType;
        this.sourceOrderType = sourceOrderType;
        this.useInspectionFilter = useInspectionFilter;
        this.extraFilter = extraFilter;
    }

    public NoticeBillDirection getDirection() {
        return direction;
    }

    public String getLabel() {
        return label;
    }

    public String getDefaultFormId() {
        return defaultFormId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getSourceOrderType() {
        return sourceOrderType;
    }

    public boolean isUseInspectionFilter() {
        return useInspectionFilter;
    }

    public String getExtraFilter() {
        return extraFilter;
    }

    public String getCode() {
        return name();
    }

    /**
     * 对已有未审核单据确认（不 Save 新建、不改 WMS 库存）。
     * 生产/委外领退、生产/委外补料：回写实发/实退后立即审核（部分领退同样审核）；
     * 生产/委外补料额外走 WorkflowAudit；收料通知单仍走「已审核通知 → 新建入库」。
     * 销售发货通知见 {@link #isAuditedSourcePushBill()}（扫已审核 → 下推出库）。
     */
    public boolean isUnauditedWorkflowBill() {
        return this == PRODUCTION_ISSUE
                || this == PRODUCTION_FEED
                || this == PRODUCTION_RETURN
                || this == PRODUCTION_RET_STOCK
                || this == OUTSOURCE_ISSUE
                || this == OUTSOURCE_FEED
                || this == OUTSOURCE_RETURN
                || this == OTHER_IN
                || this == OTHER_OUT
                || this == PURCHASE_RETURN;
    }

    /**
     * 扫已审核源单后下推下游单据（不改 WMS 库存、不审核源单本身）。
     * 销售发货通知：已审核 → 下推销售出库并审核。
     */
    public boolean isAuditedSourcePushBill() {
        return this == SALES_DELIVERY;
    }

    /**
     * PDA 确认时不改 WMS 库存，仅驱动金蝶（审核未审单据 / 下推已审源单）。
     */
    public boolean isErpConfirmWithoutWmsStock() {
        return isUnauditedWorkflowBill() || isAuditedSourcePushBill();
    }

    /**
     * 提交后走金蝶 WorkflowAudit（需绑定金蝶用户），否则普通 Audit。
     */
    public boolean isWorkflowAuditBill() {
        return this == PRODUCTION_FEED
                || this == OUTSOURCE_FEED;
    }

    public static NoticeBillType fromCode(String code) {
        if (!StringUtils.hasText(code)) {
            return PURCHASE_RECEIVE;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(t -> t.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "不支持的单据类型: " + code));
    }

    public static List<NoticeBillType> byDirection(NoticeBillDirection direction) {
        return Arrays.stream(values())
                .filter(t -> t.direction == direction)
                .toList();
    }
}
