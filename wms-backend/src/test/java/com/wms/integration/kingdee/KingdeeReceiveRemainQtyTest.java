package com.wms.integration.kingdee;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KingdeeReceiveRemainQtyTest {

    @Test
    void prefersRemainField() {
        assertEquals(0, KingdeeReceiveRemainQty.resolve(
                BigDecimal.ZERO, bd("100"), bd("100"), bd("0")).compareTo(BigDecimal.ZERO));
        assertEquals(0, KingdeeReceiveRemainQty.resolve(
                bd("30"), bd("100"), bd("100"), bd("90")).compareTo(bd("30")));
    }

    @Test
    void usesQualifiedMinusJoin() {
        assertEquals(0, KingdeeReceiveRemainQty.resolve(
                null, bd("100"), bd("100"), bd("40")).compareTo(bd("60")));
    }

    @Test
    void fallsBackToReceiveWhenQualifiedMissing() {
        assertEquals(0, KingdeeReceiveRemainQty.resolve(
                null, null, bd("80"), bd("20")).compareTo(bd("60")));
    }

    @Test
    void returnsNullWhenNoBasis() {
        assertNull(KingdeeReceiveRemainQty.resolve(null, null, null, null));
        assertNull(KingdeeReceiveRemainQty.resolve(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
