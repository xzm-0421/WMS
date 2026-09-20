package com.wms.mes.kingdee;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesKingdeeFormIdsTest {

    @Test
    void routeCandidatesIncludeCloudAndOpenApiIds() {
        List<String> ids = MesKingdeeFormIds.candidates("ENG_ROUTE");
        assertTrue(ids.contains("ENG_ROUTE"));
        assertTrue(ids.contains("ENG_Route"));
    }

    @Test
    void opPlanCandidatesFallBackToWorkshopForm() {
        List<String> ids = MesKingdeeFormIds.candidates("PRD_PROCESSCHEDULE");
        assertEquals("PRD_PROCESSCHEDULE", ids.get(0));
        assertTrue(ids.contains("SFC_OperationPlanning"));
    }
}
