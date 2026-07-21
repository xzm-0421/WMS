package com.wms.integration.kingdee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeResponseMessageFormatterTest {

    @Test
    void formatsErrorsArrayMessage() {
        String raw = "[{\"FieldName\":null,\"Message\":\"采购入库单超额!\\r\\n规则说明\",\"DIndex\":0}]";
        String formatted = KingdeeResponseMessageFormatter.format(raw);
        assertTrue(formatted.contains("采购入库单超额"));
        assertTrue(!formatted.contains("\\r\\n"));
    }
}
