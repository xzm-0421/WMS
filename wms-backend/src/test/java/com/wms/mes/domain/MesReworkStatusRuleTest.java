package com.wms.mes.domain;

import com.wms.mes.MesConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesReworkStatusRuleTest {

    @Test
    void completeOnlyFromReworking() {
        assertTrue(MesReworkStatusRule.canComplete(MesConstants.REWORK_REWORKING));
        assertFalse(MesReworkStatusRule.canComplete(MesConstants.REWORK_DONE));
        assertFalse(MesReworkStatusRule.canComplete(MesConstants.REWORK_SECONDARY));
        assertFalse(MesReworkStatusRule.canComplete(MesConstants.REWORK_CLOSED));
        assertFalse(MesReworkStatusRule.canComplete(null));
    }

    @Test
    void secondaryFromReworkingOrDone() {
        assertTrue(MesReworkStatusRule.canSecondary(MesConstants.REWORK_REWORKING));
        assertTrue(MesReworkStatusRule.canSecondary(MesConstants.REWORK_DONE));
        assertFalse(MesReworkStatusRule.canSecondary(MesConstants.REWORK_SECONDARY));
        assertFalse(MesReworkStatusRule.canSecondary(MesConstants.REWORK_CLOSED));
    }

    @Test
    void closeFromDoneOrSecondary() {
        assertTrue(MesReworkStatusRule.canClose(MesConstants.REWORK_DONE));
        assertTrue(MesReworkStatusRule.canClose(MesConstants.REWORK_SECONDARY));
        assertFalse(MesReworkStatusRule.canClose(MesConstants.REWORK_REWORKING));
        assertFalse(MesReworkStatusRule.canClose(MesConstants.REWORK_CLOSED));
    }
}
