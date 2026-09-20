package com.wms.mes.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesQtyControlTest {

    @Test
    void ratioPriorityProcessThenProductThenDefault() {
        assertEquals(new BigDecimal("0.1"), MesQtyControl.resolveRatio(
                new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3")));
        assertEquals(new BigDecimal("0.2"), MesQtyControl.resolveRatio(
                null, new BigDecimal("0.2"), new BigDecimal("0.3")));
        assertEquals(new BigDecimal("0.3"), MesQtyControl.resolveRatio(null, null, new BigDecimal("0.3")));
        assertEquals(BigDecimal.ZERO, MesQtyControl.resolveRatio(null, null, null));
    }

    @Test
    void maxAllowedRespectsOverReceive() {
        BigDecimal max = MesQtyControl.maxAllowedQty(bd("100"), bd("90"), bd("0.1"));
        assertEquals(0, max.compareTo(bd("20")));
        assertTrue(MesQtyControl.withinLimit(bd("20"), max));
        assertFalse(MesQtyControl.withinLimit(bd("20.01"), max));
        assertFalse(MesQtyControl.withinLimit(bd("0"), max));
    }

    @Test
    void normalCompletedWhenReportedReachesPlan() {
        assertTrue(MesQtyControl.normalProcessCompleted(bd("10"), bd("10")));
        assertTrue(MesQtyControl.normalProcessCompleted(bd("10"), bd("12")));
        assertFalse(MesQtyControl.normalProcessCompleted(bd("10"), bd("9")));
        assertFalse(MesQtyControl.normalProcessCompleted(bd("0"), bd("0")));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
