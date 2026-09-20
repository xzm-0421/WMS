package com.wms.integration.kingdee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KingdeeBillNoParserTest {

    @Test
    void parsePlainCgslBillNo() {
        assertEquals("CGSL240801721", KingdeeBillNoParser.parse("CGSL240801721"));
        assertEquals("CGSL240801721", KingdeeBillNoParser.parse("cgsl240801721"));
    }

    @Test
    void parseKingdeeJsonUsesFBillNo() {
        assertEquals("CGSL240801721", KingdeeBillNoParser.parse(
                "{\"FormId\":\"PUR_ReceiveBill\",\"FBillNo\":\"CGSL240801721\"}"));
    }

    @Test
    void parsePrefixedAndEmbedded() {
        assertEquals("CGSL240801721", KingdeeBillNoParser.parse("RN:CGSL240801721"));
        assertEquals("CGSL240801721", KingdeeBillNoParser.parse("收料通知单 CGSL240801721 已审核"));
    }
}
