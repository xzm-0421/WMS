package com.wms.print;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 套打 DocumentData 结构构建辅助
 */
public final class PrintDocumentHelper {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private PrintDocumentHelper() {
    }

    public static String fmtDate(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATE);
    }

    public static String fmtDate(LocalDate dt) {
        return dt == null ? "" : dt.format(DATE);
    }

    public static String fmtDateTime(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATETIME);
    }

    public static Map<String, Object> document(
            String docId,
            String mainSourceId,
            String entrySourceId,
            Map<String, Object> header,
            List<Map<String, Object>> entries,
            Map<String, Map<String, Object>> materials) {
        Map<String, Object> related = new LinkedHashMap<>();
        if (materials != null && !materials.isEmpty()) {
            related.put("t_bd_material", materials);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("docId", docId);
        doc.put("mainSourceId", mainSourceId);
        if (entrySourceId != null) {
            doc.put("entrySourceId", entrySourceId);
        }
        doc.put("header", header);
        doc.put("entries", entries == null ? List.of() : entries);
        doc.put("relatedData", related);
        return doc;
    }

    public static Map<String, Object> headerBill(String billNo) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("FBillNo", billNo);
        h.put("FBarCode", billNo);
        return h;
    }

    public static Map<String, Object> materialRow(String materialCode, String name, String spec, String unit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("FMasterId", materialCode);
        m.put("FNumber", materialCode);
        m.put("FName", name);
        m.put("FModel", spec);
        m.put("FBaseUnit", unit);
        m.put("FBarCode", materialCode);
        return m;
    }
}
