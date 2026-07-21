package com.wms.common.excel;

import com.alibaba.excel.EasyExcel;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Predicate;

public final class ExcelImportHelper {

    private ExcelImportHelper() {
    }

    public static <T> List<T> readRows(MultipartFile file, Class<T> headClass) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要导入的 Excel 文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || (!name.endsWith(".xlsx") && !name.endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 .xlsx 或 .xls 格式");
        }
        return EasyExcel.read(file.getInputStream())
                .head(headClass)
                .sheet()
                .headRowNumber(1)
                .doReadSync();
    }

    public static <T> void writeTemplate(OutputStream outputStream, Class<T> headClass, String sheetName) {
        EasyExcel.write(outputStream, headClass).sheet(sheetName).doWrite(List.of());
    }

    public static <T> void writeTemplate(OutputStream outputStream, Class<T> headClass, String sheetName,
                                         List<T> sampleRows) {
        EasyExcel.write(outputStream, headClass).sheet(sheetName).doWrite(sampleRows);
    }

    public static void requirePositiveQty(java.math.BigDecimal qty, int rowNum, String fieldLabel) {
        if (qty == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "第 " + rowNum + " 行「" + fieldLabel + "」不能为空，请填写大于 0 的数字");
        }
        if (qty.signum() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "第 " + rowNum + " 行「" + fieldLabel + "」必须大于 0");
        }
    }

    /** 检测用户把订单列表/错误格式数据填进导入模板的情况 */
    public static void detectPurchaseOrderFormat(PurchaseOrderImportRow row, int rowNum) {
        int signals = 0;
        String supplier = row.getSupplierCode() != null ? row.getSupplierCode().trim() : "";
        String warehouse = row.getWarehouseCode() != null ? row.getWarehouseCode().trim() : "";
        String material = row.getMaterialCode() != null ? row.getMaterialCode().trim() : "";
        String materialName = row.getMaterialName() != null ? row.getMaterialName().trim() : "";
        String batch = row.getBatchNo() != null ? row.getBatchNo().trim() : "";

        if (supplier.matches("(?i)P0?\\d{10,}")) {
            signals++;
        }
        if (supplier.contains("供应商") || warehouse.contains("供应商")) {
            signals++;
        }
        if (material.contains("仓") || warehouse.contains("仓")) {
            signals++;
        }
        if (materialName.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            signals++;
        }
        if (batch.contains("收货") || batch.contains("完成") || batch.contains("待")) {
            signals++;
        }

        if (signals >= 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "第 " + rowNum + " 行数据列与模板不匹配。"
                            + "导入的是「物料明细」，不是订单列表。"
                            + "A列=供应商编码(如SUP001)，B列=仓库编码(如WH01)，"
                            + "C列=物料编码，D列=物料名称，E列=单位，F列=订单数量，G列=批次号。"
                            + "请重新下载模板，一行物料填一行，不要填订单号/状态/日期。");
        }
    }

    public static void requireText(String value, int rowNum, String fieldLabel) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "第 " + rowNum + " 行「" + fieldLabel + "」不能为空，请使用系统模板或检查表头");
        }
    }

    public static <T> List<T> filterBlankRows(List<T> rows, Predicate<T> blankChecker) {
        return rows.stream().filter(row -> !blankChecker.test(row)).toList();
    }
}
