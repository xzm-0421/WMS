package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeePurMrbActualQtyBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesActualQtyIntoPurMrbEntry() throws Exception {
        String json = KingdeePurMrbActualQtyBuilder.build(
                objectMapper,
                3001L,
                "CGTL20260825001",
                List.of(new KingdeePurMrbActualQtyBuilder.Line(4002L, new BigDecimal("80"))));

        JsonNode root = objectMapper.readTree(json);
        JsonNode needUpdate = root.get("NeedUpDateFields");
        assertTrue(needUpdate.isArray());
        assertEquals("FPURMRBENTRY", needUpdate.get(0).asText());
        assertTrue(needUpdate.toString().contains("\"FRMREALQTY\""));

        assertEquals(3001L, root.path("Model").path("FID").asLong());
        JsonNode entry = root.path("Model").path("FPURMRBENTRY").get(0);
        assertEquals(4002L, entry.get("FEntryID").asLong());
        // 退料实退按 PDA 实际填写数量回写，而非退料单原计划数量
        assertEquals(0, new BigDecimal("80").compareTo(entry.get("FRMREALQTY").decimalValue()));
    }

    @Test
    void rejectsLinesWithoutEntryId() {
        assertThrows(IllegalStateException.class, () -> KingdeePurMrbActualQtyBuilder.build(
                objectMapper,
                3001L,
                "CGTL20260825001",
                List.of(new KingdeePurMrbActualQtyBuilder.Line(null, new BigDecimal("80")))));
    }
}
