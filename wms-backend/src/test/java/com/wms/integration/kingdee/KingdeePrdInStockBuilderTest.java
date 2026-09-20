package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeePrdInStockBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildIncludesMorptSourceLinkAndQty() throws Exception {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        props.setPrdInStockMorptLinkRuleId("PRD_MORPT2INSTOCK");
        props.setPrdInStockMorptLinkSTableName("T_PRD_MORPTENTRY");

        KingdeePrdInStockRequest req = KingdeePrdInStockRequest.builder()
                .batchNo("INB20260816001")
                .billDate(LocalDate.of(2026, 8, 16))
                .workShopCode("WS01")
                .note("unit-test")
                .lines(List.of(KingdeePrdInStockRequest.Line.builder()
                        .materialCode("FG-TEST-001")
                        .materialName("成品A")
                        .unitCode("Pcs")
                        .mustQty(new BigDecimal("30"))
                        .realQty(new BigDecimal("30"))
                        .workShopCode("WS01")
                        .warehouseCode("CK004")
                        .batchNo("B20260816")
                        .moBillNo("MO20260816001")
                        .moId(8001L)
                        .moEntryId(80011L)
                        .moEntrySeq(1)
                        .srcEntryId(90011L)
                        .srcInterId(90001L)
                        .srcBillNo("MORPT20260816001")
                        .srcEntrySeq(1)
                        .inStockType("1")
                        .build()))
                .build();

        String json = KingdeePrdInStockBuilder.build(objectMapper, props, req);
        JsonNode root = objectMapper.readTree(json);
        JsonNode entry = root.path("Model").path("FEntity").get(0);

        assertEquals("SCRKD01_SYS", root.path("Model").path("FBillType").path("FNumber").asText());
        assertEquals("30", entry.path("FRealQty").asText());
        assertEquals("30", entry.path("FMustQty").asText());
        assertEquals("30", entry.path("FBaseRealQty").asText());
        assertEquals("PRD_MORPT", entry.path("FSrcBillType").asText());
        assertEquals("MORPT20260816001", entry.path("FSrcBillNo").asText());
        assertEquals(90001L, entry.path("FSrcInterId").asLong());
        assertEquals(90011L, entry.path("FSrcEntryId").asLong());
        assertEquals("CK004", entry.path("FStockId").path("FNumber").asText());
        assertEquals("B20260816", entry.path("FLot").path("FNumber").asText());
        assertEquals("WS01", entry.path("FWorkShopId1").path("FNumber").asText());

        JsonNode link = entry.path("FEntity_Link").get(0);
        assertEquals("PRD_MORPT2INSTOCK", link.path("FEntity_Link_FRuleId").asText());
        assertEquals("T_PRD_MORPTENTRY", link.path("FEntity_Link_FSTableName").asText());
        assertEquals("90001", link.path("FEntity_Link_FSBillId").asText());
        assertEquals("90011", link.path("FEntity_Link_FSId").asText());
        assertEquals("30", link.path("FEntity_Link_FBaseQty").asText());
        assertEquals("30", link.path("FEntity_Link_FBasePrdRealQty").asText());
        assertTrue(root.path("Model").path("FDescription").asText().contains("unit-test"));
    }
}
