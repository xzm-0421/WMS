package com.wms.common.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseOrderImportRow {

    @ExcelProperty(value = "供应商编码", index = 0)
    private String supplierCode;

    @ExcelProperty(value = "仓库编码", index = 1)
    private String warehouseCode;

    @ExcelProperty(value = "物料编码", index = 2)
    private String materialCode;

    @ExcelProperty(value = "物料名称", index = 3)
    private String materialName;

    @ExcelProperty(value = "单位", index = 4)
    private String unitCode;

    @ExcelProperty(value = "订单数量", index = 5, converter = BigDecimalFlexibleConverter.class)
    private BigDecimal orderQty;

    @ExcelProperty(value = "批次号", index = 6)
    private String batchNo;

    public static PurchaseOrderImportRow sample() {
        PurchaseOrderImportRow row = new PurchaseOrderImportRow();
        row.setSupplierCode("SUP001");
        row.setWarehouseCode("WH01");
        row.setMaterialCode("MAT001");
        row.setMaterialName("示例物料");
        row.setUnitCode("PCS");
        row.setOrderQty(new BigDecimal("100"));
        row.setBatchNo("B001");
        return row;
    }
}
