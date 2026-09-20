package com.wms.integration.kingdee;

import com.wms.integration.kingdee.dto.KingdeeReceiveBillLineVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 销售退货通知单复用收料单明细解析器的列布局，
 * 「已入库关联量」列必须用非数值字段占位，否则可退量会被算成 0。
 */
class SalReturnNoticeFieldKeysTest {

    private static final String RETURN_QTY = "150";

    @Test
    void remainQtyKeepsNoticeQtyInsteadOfZero() {
        List<String> row = buildRow(new KingdeeCloudProperties().getSalReturnNoticeDetailFieldKeys());

        KingdeeReceiveBillLineVo line = KingdeeReceiveBillDetailRowParser.mapLine(row, 1);

        assertEquals(0, new BigDecimal(RETURN_QTY).compareTo(line.getPlanQty()));
        assertNotEquals(0, BigDecimal.ZERO.compareTo(line.getRemainInStockBaseQty()),
                "退货通知可退量被算成 0，提交会被「超过收料单剩余可入库 0」误拦");
        assertEquals(0, new BigDecimal(RETURN_QTY).compareTo(line.getRemainInStockBaseQty()));
    }

    /** 按 FieldKeys 顺序造一行 Query 返回值：数量列填退货数，其余填对应字段的文本样例。 */
    private List<String> buildRow(String fieldKeys) {
        List<String> row = new ArrayList<>();
        for (String key : fieldKeys.split(",")) {
            row.add(switch (key.trim()) {
                case "FQty" -> RETURN_QTY;
                case "FBillNo" -> "XSTHTZ20260825001";
                case "FID" -> "100001";
                case "FDate" -> "2026-08-25";
                case "FMaterialId.FNumber" -> "MD5593";
                case "FMaterialId.FName" -> "成品";
                case "FEntity_FSeq" -> "1";
                case "FEntity_FEntryID" -> "200001";
                case "FLot.FNumber" -> "LOT20260825";
                case "FUnitID.FNumber" -> "PCS";
                case "FStockId.FNumber" -> "CK004";
                default -> "TEXT";
            });
        }
        return row;
    }
}
