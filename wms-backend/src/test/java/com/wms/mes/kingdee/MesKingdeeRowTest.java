package com.wms.mes.kingdee;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void parsesInnerCodeVariants() {
        MesKingdeeRow row = MesKingdeeRow.parse(
                "FMaterialID,FMaterialId.FNumber,FProductId",
                List.of("123456.0", "CP01", "987654"));
        assertEquals(123456L, row.longVal("FMaterialID"));
        assertEquals(123456L, row.longVal("FMATERIALID", "FMaterialId"));
        assertEquals(987654L, row.longVal("FProductId", "FPRODUCTID"));
        assertEquals(123456, row.intVal("FMaterialID"));
        assertNull(row.longVal("FNotExist"));
    }
}
