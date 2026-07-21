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
            "生产入库单",
            "PRD_INSTOCK",
            "PRODUCTION_IN",
            "PRODUCTION_IN",
            false,
            ""),
    PRODUCTION_RETURN(
            NoticeBillDirection.INBOUND,
            "生产领料单",
            "PRD_PickMtrl",
            "PRODUCTION_RETURN",
            "PRODUCTION_RETURN",
            false,
            ""),
    OUTSOURCE_RETURN(
            NoticeBillDirection.INBOUND,
            "委外领料单",
            "SUB_PickMtrl",
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
            "生产用料清单",
            "PRD_PPBOM",
            "PRODUCTION_OUT",
            "PRODUCTION_ISSUE",
            false,
            ""),
    OUTSOURCE_ISSUE(
            NoticeBillDirection.OUTBOUND,
            "委外用料清单",
            "SUB_PPBOM",
            "OUTSOURCE_OUT",
            "OUTSOURCE_ISSUE",
            false,
            ""),
    OTHER_OUT(
            NoticeBillDirection.OUTBOUND,
            "其他出库单",
            "STK_MisDelivery",
            "OTHER_OUT",
            "OTHER_OUT",
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
