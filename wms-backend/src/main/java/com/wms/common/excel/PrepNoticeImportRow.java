package com.wms.common.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrepNoticeImportRow {

    @ExcelProperty(value = "生产计划号", index = 0)
    private String productionPlanNo;

    @ExcelProperty(value = "仓库编码", index = 1)
    private String warehouseCode;

    @ExcelProperty(value = "物料编码", index = 2)
    private String materialCode;

    @ExcelProperty(value = "物料名称", index = 3)
    private String materialName;

    @ExcelProperty(value = "需求数量", index = 4, converter = BigDecimalFlexibleConverter.class)
    private BigDecimal demandQty;

    public static PrepNoticeImportRow sample() {
        PrepNoticeImportRow row = new PrepNoticeImportRow();
        row.setProductionPlanNo("PLAN001");
        row.setWarehouseCode("WH01");
        row.setMaterialCode("MAT001");
        row.setMaterialName("示例物料");
        row.setDemandQty(new BigDecimal("50"));
        return row;
    }
}
