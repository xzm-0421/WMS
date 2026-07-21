package com.wms.integration.kingdee;

import com.wms.barcode.util.BatchNoNormalizer;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillLineVo;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 解析 ExecuteBillQuery 收料通知单明细行（扁平分录字段）。
 * 字段顺序与 {@link KingdeeCloudProperties#getReceiveBillDetailFieldKeys()} 一致。
 */
public final class KingdeeReceiveBillDetailRowParser {

    private static final int IDX_BILL_NO = 0;
    private static final int IDX_BILL_ID = 1;
    private static final int IDX_BILL_DATE = 2;
    private static final int IDX_SUPPLIER_CODE = 3;
    private static final int IDX_SUPPLIER_NAME = 4;
    private static final int IDX_MATERIAL_CODE = 5;
    private static final int IDX_MATERIAL_NAME = 6;
    private static final int IDX_ACT_RECEIVE = 7;
    private static final int IDX_RECEIVE_BASE = 8;
    private static final int IDX_STOCK_BASE = 9;
    private static final int IDX_IN_STOCK_JOIN = 10;
    private static final int IDX_BASE_UNIT = 11;
    private static final int IDX_SEQ = 12;
    private static final int IDX_ENTRY_ID = 13;
    private static final int IDX_LOT = 14;
    private static final int IDX_UNIT = 15;
    private static final int IDX_STOCK = 16;

    private KingdeeReceiveBillDetailRowParser() {
    }

    public static String billNo(List<String> row) {
        return cell(row, IDX_BILL_NO).trim();
    }

    public static Long billId(List<String> row) {
        return parseLong(cell(row, IDX_BILL_ID));
    }

    public static LocalDate billDate(List<String> row) {
        return parseDate(cell(row, IDX_BILL_DATE));
    }

    public static String supplierCode(List<String> row) {
        return cell(row, IDX_SUPPLIER_CODE);
    }

    public static String supplierName(List<String> row) {
        return cell(row, IDX_SUPPLIER_NAME);
    }

    public static String warehouseCode(List<String> row) {
        return cell(row, IDX_STOCK);
    }

    public static KingdeeReceiveBillLineVo mapLine(List<String> row, int fallbackSeq) {
        BigDecimal receiveBase = parseDecimal(cell(row, IDX_RECEIVE_BASE));
        BigDecimal stockBase = parseDecimal(cell(row, IDX_STOCK_BASE));
        BigDecimal inStockJoinBase = parseDecimal(cell(row, IDX_IN_STOCK_JOIN));
        BigDecimal baseUnit = parseDecimal(cell(row, IDX_BASE_UNIT));
        BigDecimal actReceive = parseDecimal(cell(row, IDX_ACT_RECEIVE));
        BigDecimal receiveBaseQty = receiveBase.compareTo(BigDecimal.ZERO) > 0 ? receiveBase : stockBase;
        if (receiveBaseQty.compareTo(BigDecimal.ZERO) <= 0) {
            receiveBaseQty = baseUnit.compareTo(BigDecimal.ZERO) > 0 ? baseUnit : actReceive;
        }
        BigDecimal remain = receiveBaseQty.subtract(inStockJoinBase);
        if (remain.compareTo(BigDecimal.ZERO) < 0) {
            remain = BigDecimal.ZERO;
        }
        int seq = parseInt(cell(row, IDX_SEQ), fallbackSeq);
        return KingdeeReceiveBillLineVo.builder()
                .lineNo(seq > 0 ? seq : fallbackSeq)
                .materialCode(cell(row, IDX_MATERIAL_CODE))
                .materialName(cell(row, IDX_MATERIAL_NAME))
                .planQty(actReceive)
                .qualifiedQty(receiveBase)
                .stockBaseQty(stockBase)
                .baseUnitQty(baseUnit.compareTo(BigDecimal.ZERO) > 0 ? baseUnit : receiveBaseQty)
                .inStockJoinBaseQty(inStockJoinBase)
                .remainInStockBaseQty(remain)
                .batchNo(BatchNoNormalizer.normalize(cell(row, IDX_LOT)))
                .unitCode(StringUtils.hasText(cell(row, IDX_UNIT)) ? cell(row, IDX_UNIT) : "PCS")
                .stockWarehouseCode(cell(row, IDX_STOCK))
                .entryId(parseLong(cell(row, IDX_ENTRY_ID)))
                .receivedQty(BigDecimal.ZERO)
                .build();
    }

    private static String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }

    private static LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            String d = s.length() >= 10 ? s.substring(0, 10) : s;
            return LocalDate.parse(d);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String s) {
        try {
            return new BigDecimal(s == null || s.isBlank() ? "0" : s.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            if (!StringUtils.hasText(s)) {
                return fallback;
            }
            return Integer.parseInt(s.trim().split("\\.")[0]);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Long parseLong(String s) {
        try {
            if (!StringUtils.hasText(s)) {
                return null;
            }
            return Long.parseLong(s.trim().split("\\.")[0]);
        } catch (Exception e) {
            return null;
        }
    }
}
