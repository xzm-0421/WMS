package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillLineVo;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeReceiveBillViewParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesFPrefixFieldsFromViewResult() throws Exception {
        String json = """
                {
                  "Id": 169242,
                  "FBillNo": "CGSL260601129",
                  "FDate": "2026-06-09T00:00:00",
                  "FDocumentStatus": "C",
                  "FSupplierId": {
                    "FNumber": "MJ484",
                    "FName": [{"Key": 2052, "Value": "深圳市梦金园珠宝首饰有限公司"}]
                  },
                  "PUR_ReceiveEntry": [
                    {
                      "FSeq": 1,
                      "FMaterialId": {
                        "FNumber": "ZL0035.L-0001N-04",
                        "FName": [{"Key": 2052, "Value": "镶件（镶嵌天然石 ）"}],
                        "FSpecification": [{"Key": 2052, "Value": "6.44*10.18*28.81"}]
                      },
                      "FMaterialDesc": [{"Key": 2052, "Value": "镶件（镶嵌天然石 ）"}],
                      "FBaseUnitId": {"FNumber": "Pcs"},
                      "FActReceiveQty": 400.0,
                      "FReceiveBaseQty": 400.0,
                      "FLot_Text": " "
                    },
                    {
                      "FSeq": 2,
                      "FMaterialId": {
                        "FNumber": "ZL0033.L-0001N-05",
                        "FName": [{"Key": 2052, "Value": "头珠组件（包装+镶嵌天然石）"}],
                        "FSpecification": [{"Key": 2052, "Value": "10.0*19.85*29.57"}]
                      },
                      "FMaterialDesc": [{"Key": 2052, "Value": "头珠组件（包装+镶嵌天然石）"}],
                      "FBaseUnitId": {"FNumber": "Pcs"},
                      "FActReceiveQty": 353.0,
                      "FReceiveBaseQty": 353.0
                    }
                  ]
                }
                """;
        JsonNode billNode = objectMapper.readTree(json);
        KingdeeReceiveBillVo bill = KingdeeReceiveBillViewParser.parse(billNode);

        assertNotNull(bill);
        assertEquals("CGSL260601129", bill.getBillNo());
        assertEquals("MJ484", bill.getSupplierCode());
        assertEquals("深圳市梦金园珠宝首饰有限公司", bill.getSupplierName());
        assertEquals(2, bill.getTotalLines());

        List<KingdeeReceiveBillLineVo> lines = bill.getLines();
        assertEquals(2, lines.size());

        KingdeeReceiveBillLineVo line1 = lines.get(0);
        assertEquals(1, line1.getLineNo());
        assertEquals("ZL0035.L-0001N-04", line1.getMaterialCode());
        assertEquals("镶件（镶嵌天然石 ）", line1.getMaterialName());
        assertEquals("镶件（镶嵌天然石 ）", line1.getMaterialDesc());
        assertEquals("6.44*10.18*28.81", line1.getSpecification());
        assertEquals("Pcs", line1.getUnitCode());
        assertEquals(0, line1.getPlanQty().compareTo(new BigDecimal("400")));
        assertEquals(0, line1.getQualifiedQty().compareTo(new BigDecimal("400")));

        KingdeeReceiveBillLineVo line2 = lines.get(1);
        assertEquals(2, line2.getLineNo());
        assertEquals("ZL0033.L-0001N-05", line2.getMaterialCode());
        assertEquals(0, line2.getPlanQty().compareTo(new BigDecimal("353")));
        assertEquals(0, line2.getQualifiedQty().compareTo(new BigDecimal("353")));
    }

    @Test
    void fallsBackToNonFPrefixFields() throws Exception {
        String json = """
                {
                  "BillNo": "CGSL260601129",
                  "PUR_ReceiveEntry": [{
                    "Seq": 1,
                    "MaterialID": {"Number": "MAT001", "Name": [{"Key": 2052, "Value": "物料A"}]},
                    "BaseUnitId": {"Number": "Pcs"},
                    "ActReceiveQty": 10,
                    "ReceiveBaseQty": 8
                  }]
                }
                """;
        JsonNode billNode = objectMapper.readTree(json);
        KingdeeReceiveBillLineVo line = KingdeeReceiveBillViewParser.parseMaterialLines(billNode).get(0);
        assertEquals("MAT001", line.getMaterialCode());
        assertEquals("Pcs", line.getUnitCode());
        assertEquals(0, line.getPlanQty().compareTo(new BigDecimal("10")));
        assertEquals(0, line.getQualifiedQty().compareTo(new BigDecimal("8")));
    }

    @Test
    void parsesPoOrderLinkFromReceiveEntry() throws Exception {
        String json = """
                {
                  "FBillNo": "CGSL260601129",
                  "PUR_ReceiveEntry": [{
                    "FSeq": 1,
                    "FEntryID": 241297,
                    "FMaterialId": {"FNumber": "S006-A280027D-0000-A01"},
                    "FBaseUnitId": {"FNumber": "Pcs"},
                    "FActReceiveQty": 10,
                    "FReceiveBaseQty": 10,
                    "SrcFormId": "PUR_PurchaseOrder",
                    "SrcBillNo": "PWW2604000007",
                    "POORDERENTRYID": 241297,
                    "FLot": {"FNumber": "LOT20260713"}
                  }]
                }
                """;
        KingdeeReceiveBillLineVo line = KingdeeReceiveBillViewParser.parseMaterialLines(
                objectMapper.readTree(json)).get(0);
        assertEquals("PWW2604000007", line.getPoOrderNo());
        assertEquals(241297L, line.getPoOrderEntryId());
        assertEquals("LOT20260713", line.getBatchNo());
    }

    @Test
    void returnsEmptyListWhenNoEntries() throws Exception {
        JsonNode billNode = objectMapper.readTree("{\"FBillNo\":\"EMPTY001\"}");
        List<KingdeeReceiveBillLineVo> lines = KingdeeReceiveBillViewParser.parseMaterialLines(billNode);
        assertTrue(lines.isEmpty());
    }
}
