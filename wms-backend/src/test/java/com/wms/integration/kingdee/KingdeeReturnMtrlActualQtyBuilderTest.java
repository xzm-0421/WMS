package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeReturnMtrlActualQtyBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void needUpdateFieldsMustIncludeFEntity() throws Exception {
        String json = KingdeeReturnMtrlActualQtyBuilder.build(
                objectMapper,
                1001L,
                "WWT20260816001",
                List.of(new KingdeeReturnMtrlActualQtyBuilder.Line(2002L, new BigDecimal("20"))));

        JsonNode root = objectMapper.readTree(json);
        JsonNode needUpdate = root.get("NeedUpDateFields");
        assertTrue(needUpdate.isArray());
        assertEquals("FEntity", needUpdate.get(0).asText());
        assertTrue(needUpdate.toString().contains("\"FQty\""));

        JsonNode entry = root.path("Model").path("FEntity").get(0);
        assertEquals(2002L, entry.get("FEntryID").asLong());
        assertEquals(0, new BigDecimal("20").compareTo(entry.get("FQty").decimalValue()));
    }
}
