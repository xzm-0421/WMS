package com.wms.mes.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 报工数量上限：计划数量 × (1 + 超收比例) − 已报工数量。
 * 超收比例优先级：工序级 &gt; 产品级 &gt; 默认值。
 */
public final class MesQtyControl {

    private static final int SCALE = 6;

    private MesQtyControl() {
    }

    public static BigDecimal resolveRatio(BigDecimal processRatio, BigDecimal productRatio, BigDecimal defaultRatio) {
        if (processRatio != null) {
            return processRatio;
        }
        if (productRatio != null) {
            return productRatio;
        }
        return defaultRatio == null ? BigDecimal.ZERO : defaultRatio;
    }

    public static BigDecimal maxAllowedQty(BigDecimal planQty, BigDecimal reportedQty, BigDecimal overReceiveRatio) {
        BigDecimal plan = nvl(planQty);
        BigDecimal reported = nvl(reportedQty);
        BigDecimal ratio = nvl(overReceiveRatio);
        BigDecimal ceiling = plan.multiply(BigDecimal.ONE.add(ratio)).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal remain = ceiling.subtract(reported);
        return remain.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remain;
    }

    public static boolean withinLimit(BigDecimal thisQty, BigDecimal maxAllowed) {
        if (thisQty == null || thisQty.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return thisQty.compareTo(nvl(maxAllowed)) <= 0;
    }

    public static boolean normalProcessCompleted(BigDecimal planQty, BigDecimal reportedQty) {
        return nvl(reportedQty).compareTo(nvl(planQty)) >= 0 && nvl(planQty).compareTo(BigDecimal.ZERO) > 0;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
