package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeBillSubmitPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsSubmitPayloadWithBillNumber() throws Exception {
        JsonNode data = KingdeeBillSubmitPayload.build(objectMapper, "CGRK260700008", "12345");
        assertEquals(0, data.path("CreateOrgId").asInt());
        assertEquals("CGRK260700008", data.path("Numbers").get(0).asText());
        assertEquals("12345", data.path("Ids").asText());
        assertEquals(0, data.path("SelectedPostId").asInt());
        assertEquals(0, data.path("UseOrgId").asInt());
        assertEquals(false, data.path("NetworkCtrl").asBoolean());
        assertEquals(true, data.path("IgnoreInterationFlag").asBoolean());
        assertTrue(data.path("Numbers").isArray());
    }
}
