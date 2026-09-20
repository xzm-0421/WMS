package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeSalReturnStockBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildIncludesCustomerMaterialLotStockAndLink() throws Exception {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        KingdeeSalReturnStockRequest req = KingdeeSalReturnStockRequest.builder()
                .batchNo("RB001")
                .sourceBillNo("XSTH20260817001")
                .sourceBillId(9001L)
                .customerCode("CUST01")
                .billDate(LocalDate.of(2026, 8, 17))
                .lines(List.of(KingdeeSalReturnStockRequest.Line.builder()
                        .materialCode("A.02.00.00226")
                        .unitCode("Pcs")
                        .warehouseCode("CK004")
                        .batchNo("20260817")
                        .quantity(new BigDecimal("5"))
                        .sourceEntryId(8001L)
                        .sourceLineNo(1)
                        .build()))
                .build();

        String json = KingdeeSalReturnStockBuilder.build(objectMapper, props, req);
        JsonNode model = objectMapper.readTree(json).path("Model");
        assertEquals("CUST01", model.path("FRetcustId").path("FNumber").asText());
        JsonNode entry = model.path("FEntity").get(0);
        assertEquals("A.02.00.00226", entry.path("FMaterialId").path("FNumber").asText());
        assertEquals("CK004", entry.path("FStockId").path("FNumber").asText());
        assertEquals("20260817", entry.path("FLot").path("FNumber").asText());
        assertEquals("5", entry.path("FRealQty").asText());
        assertTrue(entry.path("FEntity_Link").isArray());
        assertEquals(9001L, entry.path("FEntity_Link").get(0).path("FEntity_Link_FSBillId").asLong());
        assertEquals(8001L, entry.path("FEntity_Link").get(0).path("FEntity_Link_FSId").asLong());
    }
}
