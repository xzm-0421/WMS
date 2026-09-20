package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

/**
 * 金蝶 Save 公共信封字段（与官方 WebAPI 模板一致）。
 */
public final class KingdeeSaveEnvelope {

    private KingdeeSaveEnvelope() {
    }

    public static ObjectNode createRoot(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("NeedUpDateFields");
        ArrayNode needReturn = root.putArray("NeedReturnFields");
        needReturn.add("FBillNo");
        needReturn.add("FID");
        root.put("IsDeleteEntry", "true");
        root.put("SubSystemId", "");
        root.put("IsVerifyBaseDataField", "false");
        root.put("IsEntryBatchFill", "true");
        root.put("ValidateFlag", "true");
        root.put("NumberSearch", "true");
        root.put("IsAutoAdjustField", "true");
        root.put("InterationFlags", "");
        root.put("IgnoreInterationFlag", "");
        root.put("IsControlPrecision", "false");
        root.put("ValidateRepeatJson", "true");
        return root;
    }

    public static void putNumberRef(ObjectNode parent, String field, String number) {
        if (!StringUtils.hasText(number)) {
            return;
        }
        ObjectNode ref = parent.putObject(field);
        ref.put("FNumber", number.trim());
    }

    public static void putLong(ObjectNode parent, String field, Long value) {
        if (value == null || value <= 0) {
            return;
        }
        parent.put(field, value);
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
