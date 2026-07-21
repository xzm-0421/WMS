package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeePurchaseInStockBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildProducesDirectSavePayload() throws Exception {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        KingdeePurchaseInStockRequest req = sampleRequest();

        String json = KingdeePurchaseInStockBuilder.build(objectMapper, props, req);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("true", root.path("IsDeleteEntry").asText());
        assertEquals("FBillNo", root.path("NeedReturnFields").get(0).asText());
        assertEquals(0, root.path("Model").path("FID").asInt());
        assertEquals("RKD01_SYS", root.path("Model").path("FBillTypeID").path("FNumber").asText());
        assertEquals("G1803", root.path("Model").path("FSupplierId").path("FNumber").asText());
    }

    @Test
    void buildEntryQuantityFieldsMatchRealQty() throws Exception {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        KingdeePurchaseInStockRequest req = sampleRequest();
        String json = KingdeePurchaseInStockBuilder.build(objectMapper, props, req);
        JsonNode entry = objectMapper.readTree(json).path("Model").path("FInStockEntry").get(0);

        assertEquals("61", entry.path("FRealQty").asText());
        assertEquals("61", entry.path("FPriceUnitQty").asText());
        assertEquals("61", entry.path("FPriceBaseQty").asText());
        assertEquals("61", entry.path("FStockBaseQty").asText());
        assertEquals("61", entry.path("FBaseUnitQty").asText());
        assertEquals("61", entry.path("FBaseJoinQty").asText());
        assertEquals("61", entry.path("FInStockJoinBaseQty").asText());
        assertEquals("61", entry.path("FRemainInStockQty").asText());
        assertEquals("61", entry.path("FRemainInStockBaseQty").asText());
        assertEquals("61", entry.path("FAPNotJoinQty").asText());

        JsonNode link = entry.path("FInStockEntry_Link").get(0);
        assertEquals("61", link.path("FInStockEntry_Link_FBaseJoinQty").asText());
        assertEquals("61", link.path("FInStockEntry_Link_FStockBaseQty").asText());
    }

    @Test
    void buildEntryIncludesSourceLinkAndWmsOverrides() throws Exception {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        KingdeePurchaseInStockRequest req = sampleRequest();

        String json = KingdeePurchaseInStockBuilder.build(objectMapper, props, req);
        JsonNode entry = objectMapper.readTree(json).path("Model").path("FInStockEntry").get(0);

        assertEquals("61", entry.path("FRealQty").asText());
        assertEquals("20260710", entry.path("FLot").path("FNumber").asText());
        assertEquals("CK004", entry.path("FStockId").path("FNumber").asText());
        assertEquals("CGSL240801721", entry.path("FSRCBillNo").asText());
        assertEquals(4, entry.path("F_YVZR_Integer_83g").asInt());
        assertTrue(entry.path("FCheckInComing").asBoolean());

        JsonNode link = entry.path("FInStockEntry_Link").get(0);
        assertEquals(114066L, link.path("FInStockEntry_Link_FSBillId").asLong());
        assertEquals(124933L, link.path("FInStockEntry_Link_FSId").asLong());
        assertEquals("PUR_ReceiveBill-STK_InStock", link.path("FInStockEntry_Link_FRuleId").asText());
        assertEquals(73, link.path("FInStockEntry_Link_FSTableId").asInt());
        assertEquals("61", link.path("FInStockEntry_Link_FRemainInStockBaseQty").asText());
    }

    @Test
    void buildUsesSourceLinkCarryQtyWhenProvided() throws Exception {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        KingdeeInStockEntryLink sourceLink = KingdeeInStockEntryLink.fromReceiveLine(
                props, 114066L, 124933L, new BigDecimal("61"),
                new BigDecimal("100"), new BigDecimal("80"));
        KingdeePurchaseInStockRequest req = KingdeePurchaseInStockRequest.builder()
                .sourceBillNo("CGSL240801721")
                .supplierCode("G1803")
                .lines(List.of(KingdeePurchaseInStockRequest.Line.builder()
                        .materialCode("SJ-HB01-002-29")
                        .warehouseCode("CK004")
                        .quantity(new BigDecimal("61"))
                        .sourceLineNo(4)
                        .sourceBillId(114066L)
                        .sourceEntryId(124933L)
                        .sourceLink(sourceLink)
                        .build()))
                .build();

        String json = KingdeePurchaseInStockBuilder.build(objectMapper, props, req);
        JsonNode link = objectMapper.readTree(json)
                .path("Model").path("FInStockEntry").get(0)
                .path("FInStockEntry_Link").get(0);

        assertEquals("100", link.path("FInStockEntry_Link_FRemainInStockBaseQtyOld").asText());
        assertEquals("80", link.path("FInStockEntry_Link_FBaseUnitQtyOld").asText());
        assertTrue(link.path("FInStockEntry_Link_FRemainInStockBaseQty").asText().equals("61"));
    }

    @Test
    void buildEntryIncludesPoOrderAndLotFallback() throws Exception {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        KingdeePurchaseInStockRequest req = KingdeePurchaseInStockRequest.builder()
                .sourceBillNo("CGSL240801721")
                .supplierCode("G1803")
                .billDate(LocalDate.of(2026, 7, 13))
                .lines(List.of(KingdeePurchaseInStockRequest.Line.builder()
                        .materialCode("S006-A280027D-0000-A01")
                        .warehouseCode("CK004")
                        .quantity(new BigDecimal("10"))
                        .sourceLineNo(1)
                        .sourceBillId(114066L)
                        .sourceEntryId(124933L)
                        .poOrderNo("PWW2604000007")
                        .poOrderEntryId(241297L)
                        .build()))
                .build();

        String json = KingdeePurchaseInStockBuilder.build(objectMapper, props, req);
        JsonNode entry = objectMapper.readTree(json).path("Model").path("FInStockEntry").get(0);

        assertEquals("20260713", entry.path("FLot").path("FNumber").asText());
        assertEquals("PWW2604000007", entry.path("FPOOrderNo").asText());
        assertEquals(241297L, entry.path("FPOORDERENTRYID").asLong());
    }

    private KingdeePurchaseInStockRequest sampleRequest() {
        return KingdeePurchaseInStockRequest.builder()
                .batchNo("RB20260710001")
                .sourceBillNo("CGSL240801721")
                .supplierCode("G1803")
                .billDate(LocalDate.of(2026, 7, 10))
                .lines(List.of(KingdeePurchaseInStockRequest.Line.builder()
                        .materialCode("SJ-HB01-002-29")
                        .materialName("拆生螺杆2")
                        .unitCode("Pcs")
                        .warehouseCode("CK004")
                        .batchNo("20260710")
                        .quantity(new BigDecimal("61"))
                        .sourceLineNo(4)
                        .sourceBillId(114066L)
                        .sourceEntryId(124933L)
                        .build()))
                .build();
    }
}
