package com.wms.integration.kingdee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeReceiveBillFieldParserTest {

    @Test
    void checkIncoming_oneIsTrue() {
        assertTrue(KingdeeReceiveBillFieldParser.parseCheckIncoming("1"));
    }

    @Test
    void checkIncoming_trueStringIsTrue() {
        assertTrue(KingdeeReceiveBillFieldParser.parseCheckIncoming("true"));
        assertTrue(KingdeeReceiveBillFieldParser.parseCheckIncoming("True"));
    }

    @Test
    void checkIncoming_zeroIsFalse() {
        assertFalse(KingdeeReceiveBillFieldParser.parseCheckIncoming("0"));
    }

    @Test
    void checkIncoming_blankIsFalse() {
        assertFalse(KingdeeReceiveBillFieldParser.parseCheckIncoming(""));
        assertFalse(KingdeeReceiveBillFieldParser.parseCheckIncoming(null));
    }
}
