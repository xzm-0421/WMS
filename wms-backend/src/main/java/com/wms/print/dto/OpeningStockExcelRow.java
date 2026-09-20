package com.wms.print.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.wms.common.excel.BigDecimalFlexibleConverter;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 期初库存标签打印 Excel 导入/导出行。
 * <p>下载模板表头须与下列 {@code @ExcelProperty} 列名、顺序保持一致。
 */
@Data
public class OpeningStockExcelRow {

    @ExcelProperty(value = "仓库编码", index = 0)
    private String warehouseCode;

    @ExcelProperty(value = "仓库名称", index = 1)
    private String warehouseName;

    @ExcelProperty(value = "业务组织编码", index = 2)
    private String orgCode;

    @ExcelProperty(value = "业务组织名称", index = 3)
    private String orgName;

    @ExcelProperty(value = "物料编码", index = 4)
    private String materialCode;

    @ExcelProperty(value = "物料名称", index = 5)
    private String materialName;

    @ExcelProperty(value = "规格型号", index = 6)
    private String specification;

    @ExcelProperty(value = "批次号", index = 7)
    private String batchNo;

    @ExcelProperty(value = "生产日期", index = 8)
    private String productionDate;

    @ExcelProperty(value = "数量", index = 9, converter = BigDecimalFlexibleConverter.class)
    private BigDecimal quantity;

    @ExcelProperty(value = "入库单位", index = 10)
    private String unitCode;

    @ExcelProperty(value = "计价单位", index = 11)
    private String priceUnitCode;

    @ExcelProperty(value = "打印份数", index = 12)
    private Integer copies;

    /** 厂内 / 来料；空则按来源默认厂内 */
    @ExcelProperty(value = "标签类型", index = 13)
    private String labelFormat;

    @ExcelProperty(value = "客户/供应商", index = 14)
    private String partnerName;

    @ExcelProperty(value = "板号", index = 15)
    private String boardNo;

    @ExcelProperty(value = "包装号", index = 16)
    private String packageNo;

    public static OpeningStockExcelRow sample() {
        OpeningStockExcelRow row = new OpeningStockExcelRow();
        row.setWarehouseCode("WH01");
        row.setWarehouseName("原料仓");
        row.setOrgCode("100");
        row.setOrgName("示例组织");
        row.setMaterialCode("MAT001");
        row.setMaterialName("示例物料");
        row.setSpecification("规格A");
        row.setBatchNo("B20260101");
        row.setProductionDate("2026-01-01");
        row.setQuantity(new BigDecimal("100"));
        row.setUnitCode("PCS");
        row.setPriceUnitCode("KG");
        row.setCopies(1);
        row.setLabelFormat("厂内");
        row.setPartnerName("示例客户");
        row.setBoardNo("01");
        row.setPackageNo(null);
        return row;
    }
}
