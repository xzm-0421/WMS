package com.wms.integration.kingdee;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class KingdeeBillQueryFiltersTest {

    @Test
    void appendRecentDays_addsFDateWhenDaysPositive() {
        String base = "FDocumentStatus='C'";
        String result = KingdeeBillQueryFilters.appendRecentDays(base, 30);
        LocalDate from = LocalDate.now().minusDays(30);
        assertEquals(base + " and FDate >= '" + from + "'", result);
    }

    @Test
    void appendRecentDays_skipsWhenDaysZero() {
        String base = "FDocumentStatus='C'";
        assertSame(base, KingdeeBillQueryFilters.appendRecentDays(base, 0));
    }
}
