package com.wms.integration.kingdee;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeReceiveBillInspectionFilterTest {

    @Test
    void acceptsWhenNotIncomingInspectionAndHasRemain() {
        assertTrue(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                false, "100", "0", "0", "0", "0", "0", "0", "0", "0", null)));
    }

    @Test
    void rejectsWhenRemainIsZero() {
        assertFalse(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                false, "100", "0", "0", "100", "0", "0", "0", "0", "100", BigDecimal.ZERO)));
    }

    @Test
    void rejectsWhenJoinEqualsQualified() {
        assertFalse(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                true, "100", "100", "0", "100", "0", "0", "0", "0", "100", null)));
    }

    @Test
    void acceptsWhenIncomingInspectionAndHasRemain() {
        assertTrue(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                true, "100", "100", "0", "100", "0", "0", "0", "0", "40", null)));
    }

    @Test
    void rejectsWhenCheckEqualsRefuse() {
        assertFalse(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                true, "100", "100", "100", "0", "0", "0", "0", "0", "0", null)));
    }

    @Test
    void rejectsWhenReceiveAndQualifiedAreZero() {
        assertFalse(KingdeeReceiveBillInspectionFilter.isEligibleLine(
                KingdeeReceiveBillInspectionLine.builder()
                        .billNo("B001")
                        .checkIncoming(false)
                        .receiveQty(BigDecimal.ZERO)
                        .qualifiedQty(BigDecimal.ZERO)
                        .build()));
    }

    @Test
    void countsOnlyEligibleLinesAsMaterialLineCount() {
        List<KingdeeReceiveBillInspectionLine> rows = List.of(
                line("B001", true, "100", "100", "0", "100", "0", "0", "0", "0", "100", BigDecimal.ZERO),
                line("B001", true, "100", "100", "0", "100", "0", "0", "0", "0", "20", null));
        List<KingdeeReceiveBillInspectionLine> bills = KingdeeReceiveBillInspectionFilter.filterEligibleBills(rows);
        assertEquals(1, bills.size());
        assertEquals(1, bills.get(0).getMaterialLineCount());
    }

    private static KingdeeReceiveBillInspectionLine line(boolean checkIncoming, String receive, String check,
                                                         String refuse, String qualified, String sample,
                                                         String concession, String proc, String mtrl,
                                                         String joined, BigDecimal remain) {
        return line("B001", checkIncoming, receive, check, refuse, qualified, sample, concession, proc, mtrl,
                joined, remain);
    }

    private static KingdeeReceiveBillInspectionLine line(String billNo, boolean checkIncoming, String receive,
                                                         String check, String refuse, String qualified, String sample,
                                                         String concession, String proc, String mtrl,
                                                         String joined, BigDecimal remain) {
        return KingdeeReceiveBillInspectionLine.builder()
                .billNo(billNo)
                .supplierName("供应商A")
                .documentStatus("C")
                .checkIncoming(checkIncoming)
                .receiveQty(new BigDecimal(receive))
                .checkQty(new BigDecimal(check))
                .refuseQty(new BigDecimal(refuse))
                .qualifiedQty(new BigDecimal(qualified))
                .sampleDamageQty(new BigDecimal(sample))
                .concessionQty(new BigDecimal(concession))
                .procScrapQty(new BigDecimal(proc))
                .mtrlScrapQty(new BigDecimal(mtrl))
                .inStockJoinBaseQty(joined == null ? null : new BigDecimal(joined))
                .remainInStockBaseQty(remain)
                .build();
    }
}
