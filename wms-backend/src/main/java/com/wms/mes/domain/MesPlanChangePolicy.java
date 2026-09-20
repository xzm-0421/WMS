package com.wms.mes.domain;

import java.math.BigDecimal;

/**
 * ERP 数量变更在 MES 端的接受/拒绝规则。
 */
public final class MesPlanChangePolicy {

    public enum Decision {
        ACCEPT,
        REJECT,
        NO_CHANGE
    }

    private MesPlanChangePolicy() {
    }

    /**
     * 工单数量：未报工可增可减（不可小于 0）；已报工只能增加，且不得小于已报工数量。
     */
    public static Decision forMoQty(BigDecimal reportedQty, BigDecimal oldPlanQty, BigDecimal newPlanQty) {
        if (newPlanQty == null || newPlanQty.compareTo(BigDecimal.ZERO) < 0) {
            return Decision.REJECT;
        }
        BigDecimal reported = nvl(reportedQty);
        BigDecimal oldPlan = nvl(oldPlanQty);
        if (newPlanQty.compareTo(oldPlan) == 0) {
            return Decision.NO_CHANGE;
        }
        if (reported.compareTo(BigDecimal.ZERO) <= 0) {
            return Decision.ACCEPT;
        }
        if (newPlanQty.compareTo(reported) < 0) {
            return Decision.REJECT;
        }
        if (newPlanQty.compareTo(oldPlan) < 0) {
            return Decision.REJECT;
        }
        return Decision.ACCEPT;
    }

    /**
     * 工序计划数量：仅允许追加（增加），不允许减少。
     */
    public static Decision forOpPlanQty(BigDecimal oldPlanQty, BigDecimal newPlanQty) {
        if (newPlanQty == null || newPlanQty.compareTo(BigDecimal.ZERO) < 0) {
            return Decision.REJECT;
        }
        BigDecimal oldPlan = nvl(oldPlanQty);
        if (newPlanQty.compareTo(oldPlan) == 0) {
            return Decision.NO_CHANGE;
        }
        if (newPlanQty.compareTo(oldPlan) < 0) {
            return Decision.REJECT;
        }
        return Decision.ACCEPT;
    }

    public static String rejectMoQtyMessage() {
        return "已报工订单数量只能增加。";
    }

    public static String rejectOpPlanQtyMessage() {
        return "工序计划数量只能追加。";
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
