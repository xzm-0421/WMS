package com.wms.barcode.constant;

import cn.hutool.json.JSONUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预置拼接模板（参考金蝶云星空条码规则分段模式）
 */
public final class BarcodeTemplates {

    private BarcodeTemplates() {
    }

    public static final String MAT_BATCH_SERIAL = "MAT_BATCH_SERIAL";
    public static final String PACK_BATCH = "PACK_BATCH";
    public static final String MAT_BATCH = "MAT_BATCH";
    public static final String MAT_SERIAL = "MAT_SERIAL";
    public static final String CUSTOM = "CUSTOM";

    public static final Map<String, String> LABELS = Map.of(
            MAT_BATCH_SERIAL, "物料编码+批号+序列号",
            PACK_BATCH, "包装条码+批号",
            MAT_BATCH, "物料编码+批号",
            MAT_SERIAL, "物料编码+序列号",
            CUSTOM, "自定义分段"
    );

    public static String buildSegmentsJson(String templateCode, String separator) {
        String sep = separator == null ? "" : separator;
        List<Map<String, Object>> segments = switch (templateCode) {
            case MAT_BATCH_SERIAL -> List.of(
                    field("MATERIAL_CODE"),
                    sep(sep),
                    field("BATCH_NO"),
                    sep(sep),
                    field("SERIAL_NO")
            );
            case PACK_BATCH -> List.of(
                    field("PACK_BARCODE"),
                    sep(sep),
                    field("BATCH_NO")
            );
            case MAT_BATCH -> List.of(
                    field("MATERIAL_CODE"),
                    sep(sep),
                    field("BATCH_NO")
            );
            case MAT_SERIAL -> List.of(
                    field("MATERIAL_CODE"),
                    sep(sep),
                    field("SERIAL_NO")
            );
            default -> List.of();
        };
        return JSONUtil.toJsonStr(segments);
    }

    private static Map<String, Object> field(String source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "FIELD");
        m.put("source", source);
        return m;
    }

    private static Map<String, Object> sep(String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "SEPARATOR");
        m.put("value", value);
        return m;
    }
}
