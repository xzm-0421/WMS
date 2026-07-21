package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeBillAuditPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsAuditPayloadWithBillNumber() throws Exception {
        JsonNode data = KingdeeBillAuditPayload.build(objectMapper, "CGRK00001", "12345");
        assertEquals(0, data.path("CreateOrgId").asInt());
        assertEquals("CGRK00001", data.path("Numbers").get(0).asText());
        assertEquals("12345", data.path("Ids").asText());
        assertEquals("", data.path("InterationFlags").asText());
        assertEquals(0, data.path("UseOrgId").asInt());
        assertEquals(false, data.path("NetworkCtrl").asBoolean());
        assertEquals(true, data.path("IsVerifyProcInst").asBoolean());
        assertEquals(true, data.path("IgnoreInterationFlag").asBoolean());
        assertEquals(false, data.path("UseBatControlTimes").asBoolean());
    }

    @Test
    void buildsAuditPayloadWithoutBillId() throws Exception {
        JsonNode data = KingdeeBillAuditPayload.build(objectMapper, "CGRK00002", null);
        assertEquals(1, data.path("Numbers").size());
        assertEquals("CGRK00002", data.path("Numbers").get(0).asText());
        assertEquals("", data.path("Ids").asText());
        assertTrue(data.path("Numbers").isArray());
    }
}
