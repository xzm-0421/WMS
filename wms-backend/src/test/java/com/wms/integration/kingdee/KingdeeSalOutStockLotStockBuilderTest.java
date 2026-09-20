package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeSalOutStockLotStockBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildIncludesLotAndStock() throws Exception {
        String json = KingdeeSalOutStockLotStockBuilder.build(
                objectMapper,
                1001L,
                "XSCK001",
                List.of(new KingdeeSalOutStockLotStockBuilder.EntryUpdate(
                        2002L, "20260817", "CK004", new BigDecimal("10"))));
        assertTrue(json.contains("\"FLot\""));
        assertTrue(json.contains("\"FNumber\":\"20260817\""));
        assertTrue(json.contains("\"FStockId\""));
        assertTrue(json.contains("\"FNumber\":\"CK004\""));
        assertTrue(json.contains("\"FRealQty\":10"));
        assertTrue(json.contains("\"IsDeleteEntry\":\"false\""));
        assertTrue(json.contains("\"FEntryID\":2002"));
    }
}
