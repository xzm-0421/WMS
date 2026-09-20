package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KingdeeMoWorkShopParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolvePrefersTreeEntityWorkShopMatchingMoEntry() throws Exception {
        ObjectNode bill = objectMapper.createObjectNode();
        ObjectNode headerShop = bill.putObject("FWorkShopId");
        headerShop.put("FNumber", "BM000020");
        ObjectNode entry = bill.putArray("FTreeEntity").addObject();
        entry.put("FEntryID", 1001);
        entry.put("FSeq", 1);
        ObjectNode lineShop = entry.putObject("FWorkShopID");
        lineShop.put("FNumber", "BM000088");

        String shop = KingdeeMoWorkShopParser.resolve(bill, 1001L, 1);
        assertEquals("BM000088", shop);
    }

    @Test
    void resolveFallsBackToHeaderWhenEntryHasNoWorkShop() {
        ObjectNode bill = objectMapper.createObjectNode();
        bill.putObject("FWorkShopId").put("FNumber", "BM000020");
        ObjectNode entry = bill.putArray("FTreeEntity").addObject();
        entry.put("FEntryID", 1001);
        entry.put("FSeq", 1);

        String shop = KingdeeMoWorkShopParser.resolve(bill, 1001L, 1);
        assertEquals("BM000020", shop);
    }

    @Test
    void resolveStockPrefersTreeEntityWarehouse() {
        ObjectNode bill = objectMapper.createObjectNode();
        bill.putObject("FStockId").put("FNumber", "CK004");
        ObjectNode entry = bill.putArray("FTreeEntity").addObject();
        entry.put("FEntryID", 1001);
        entry.put("FSeq", 1);
        entry.putObject("FStockId").put("FNumber", "CK011");

        String stock = KingdeeMoWorkShopParser.resolveStock(bill, 1001L, 1);
        assertEquals("CK011", stock);
    }
}
