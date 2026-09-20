package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KingdeeSalOutStockEntryMatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void alreadyHasLotAndStockWhenViewOnlyReturnsIds() throws Exception {
        JsonNode bill = mapper.readTree("""
                {
                  "FBillNo": "XSCK260902003",
                  "FEntity": [{
                    "FENTRYID": 2001,
                    "FMaterialID": { "Id": 55 },
                    "FLot": { "Id": 88 },
                    "FStockId": { "Id": 12 }
                  }]
                }
                """);
        assertThat(KingdeeSalOutStockEntryMatcher.alreadyHasLotAndStock(bill)).isTrue();
    }

    @Test
    void matchBySourceEntryIdFromLink() throws Exception {
        JsonNode bill = mapper.readTree("""
                {
                  "FEntity": [{
                    "FEntryID": 2001,
                    "FMaterialID": { "FNumber": "M01" },
                    "FEntity_Link": [{ "FEntity_Link_FSId": 9001 }]
                  }]
                }
                """);
        List<KingdeeSalOutStockLotStockBuilder.EntryUpdate> updates = KingdeeSalOutStockEntryMatcher.match(
                bill,
                List.of(KingdeeSalOutStockEntryFill.builder()
                        .sourceEntryId(9001L)
                        .materialCode("OTHER")
                        .lotNumber("20260902")
                        .stockNumber("CK011")
                        .realQty(new BigDecimal("2"))
                        .build()));
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).entryId()).isEqualTo(2001L);
        assertThat(updates.get(0).lotNumber()).isEqualTo("20260902");
        assertThat(updates.get(0).stockNumber()).isEqualTo("CK011");
    }

    @Test
    void matchByLineSeqWhenMaterialNumberMissing() throws Exception {
        JsonNode bill = mapper.readTree("""
                {
                  "FEntity": [{
                    "FENTRYID": 2001,
                    "FSeq": 2,
                    "FMaterialID": { "Id": 55 }
                  }]
                }
                """);
        List<KingdeeSalOutStockLotStockBuilder.EntryUpdate> updates = KingdeeSalOutStockEntryMatcher.match(
                bill,
                List.of(KingdeeSalOutStockEntryFill.builder()
                        .lineNo(2)
                        .materialCode("M01")
                        .lotNumber("L1")
                        .stockNumber("CK001")
                        .build()));
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).entryId()).isEqualTo(2001L);
    }

    @Test
    void matchByIndexWhenNothingElseMatches() throws Exception {
        JsonNode bill = mapper.readTree("""
                {
                  "FEntity": [{
                    "FENTRYID": 2001,
                    "FMaterialID": { "Id": 55 }
                  }]
                }
                """);
        List<KingdeeSalOutStockLotStockBuilder.EntryUpdate> updates = KingdeeSalOutStockEntryMatcher.match(
                bill,
                List.of(KingdeeSalOutStockEntryFill.builder()
                        .materialCode("M01")
                        .lotNumber("L1")
                        .stockNumber("CK001")
                        .build()));
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).entryId()).isEqualTo(2001L);
        assertThat(updates.get(0).stockNumber()).isEqualTo("CK001");
    }
}
