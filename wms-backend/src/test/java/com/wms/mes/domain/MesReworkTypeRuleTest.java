package com.wms.mes.domain;

import com.wms.mes.MesConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesReworkTypeRuleTest {

    @Test
    void unfinishedAllowsNormalAndRework() {
        var types = MesReworkTypeRule.allowedReportTypes(false, true);
        assertTrue(types.contains(MesConstants.REPORT_NORMAL));
        assertTrue(types.contains(MesConstants.REPORT_REWORK));
        assertTrue(MesReworkTypeRule.allowNormal(false));
    }

    @Test
    void completedOnlyAllowsRework() {
        var types = MesReworkTypeRule.allowedReportTypes(true, true);
        assertFalse(types.contains(MesConstants.REPORT_NORMAL));
        assertTrue(types.contains(MesConstants.REPORT_REWORK));
        assertFalse(MesReworkTypeRule.allowNormal(true));
        assertEquals("正常工序已完成，请进行返工报工。", MesReworkTypeRule.completedHint());
    }
}
