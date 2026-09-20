package com.wms.mes.kingdee;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesKingdeeRowTest {

    @Test
    void mapsByFieldKeys() {
        MesKingdeeRow row = MesKingdeeRow.parse(
                "FNumber,FName,FMaterialID.FNumber,FMoNumber,FPlanQty",
                List.of("GX01", "冲压", "CP01", "MO001", "12.5"));
        assertEquals("GX01", row.get("FNumber"));
        assertEquals("MO001", row.get("FMoBillNo", "FMoNumber"));
        assertEquals("CP01", row.get("FMaterialID.FNumber"));
        assertEquals("CP01", row.get("FMaterialId.FNumber"));
        assertEquals(0, row.decimal("FPlanQty").compareTo(new java.math.BigDecimal("12.5")));
        assertTrue(row.isActive(false));
    }
}
