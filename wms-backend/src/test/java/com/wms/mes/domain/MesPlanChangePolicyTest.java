package com.wms.mes.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MesPlanChangePolicyTest {

    @Test
    void moQtyUnreportedCanIncreaseOrDecrease() {
        assertEquals(MesPlanChangePolicy.Decision.ACCEPT,
                MesPlanChangePolicy.forMoQty(BigDecimal.ZERO, bd("100"), bd("80")));
        assertEquals(MesPlanChangePolicy.Decision.ACCEPT,
                MesPlanChangePolicy.forMoQty(BigDecimal.ZERO, bd("100"), bd("120")));
    }

    @Test
    void moQtyReportedCannotDecrease() {
        assertEquals(MesPlanChangePolicy.Decision.REJECT,
                MesPlanChangePolicy.forMoQty(bd("10"), bd("100"), bd("90")));
        assertEquals(MesPlanChangePolicy.Decision.ACCEPT,
                MesPlanChangePolicy.forMoQty(bd("10"), bd("100"), bd("110")));
        assertEquals(MesPlanChangePolicy.Decision.REJECT,
                MesPlanChangePolicy.forMoQty(bd("50"), bd("100"), bd("40")));
    }

    @Test
    void opPlanQtyOnlyAllowsIncrease() {
        assertEquals(MesPlanChangePolicy.Decision.REJECT,
                MesPlanChangePolicy.forOpPlanQty(bd("100"), bd("90")));
        assertEquals(MesPlanChangePolicy.Decision.ACCEPT,
                MesPlanChangePolicy.forOpPlanQty(bd("100"), bd("120")));
        assertEquals(MesPlanChangePolicy.Decision.NO_CHANGE,
                MesPlanChangePolicy.forOpPlanQty(bd("100"), bd("100")));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
