package com.wms.integration.kingdee;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeReceiveBillInspectionFilterTest {

    @Test
    void acceptsWhenIncomingInspectionAndPartiallyAccounted() {
        assertTrue(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                true, "100", "100", "0", "80", "0", "0", "0", "0")));
    }

    @Test
    void rejectsWhenNotIncomingInspection() {
        assertFalse(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                false, "100", "100", "0", "80", "0", "0", "0", "0")));
    }

    @Test
    void rejectsWhenCheckNotEqualsReceive() {
        assertFalse(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                true, "100", "90", "0", "90", "0", "0", "0", "0")));
    }

    @Test
    void rejectsWhenCheckEqualsRefuse() {
        assertFalse(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                true, "100", "100", "100", "0", "0", "0", "0", "0")));
    }

    @Test
    void rejectsWhenDispositionSumEqualsCheckQty() {
        assertFalse(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                true, "100", "100", "20", "80", "0", "0", "0", "0")));
    }

    @Test
    void rejectsWhenFullyQualifiedAndSumEqualsCheck() {
        assertFalse(KingdeeReceiveBillInspectionFilter.isEligibleLine(line(
                true, "100", "100", "0", "100", "0", "0", "0", "0")));
    }

    @Test
    void keepsBillWhenAtLeastOneLineEligibleAndCountsMaterialLines() {
        List<KingdeeReceiveBillInspectionLine> rows = List.of(
                line("B001", true, "100", "100", "100", "0", "0", "0", "0", "0"),
                line("B001", true, "100", "100", "0", "80", "0", "0", "0", "0"));
        List<KingdeeReceiveBillInspectionLine> bills = KingdeeReceiveBillInspectionFilter.filterEligibleBills(rows);
        assertEquals(1, bills.size());
        assertEquals(2, bills.get(0).getMaterialLineCount());
    }

    private static KingdeeReceiveBillInspectionLine line(boolean checkIncoming, String receive, String check,
                                                         String refuse, String qualified, String sample,
                                                         String concession, String proc, String mtrl) {
        return line("B001", checkIncoming, receive, check, refuse, qualified, sample, concession, proc, mtrl);
    }

    private static KingdeeReceiveBillInspectionLine line(String billNo, boolean checkIncoming, String receive,
                                                         String check, String refuse, String qualified, String sample,
                                                         String concession, String proc, String mtrl) {
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
                .build();
    }
}
