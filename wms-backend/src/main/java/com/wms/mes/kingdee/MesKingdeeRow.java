package com.wms.mes.kingdee;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按 FieldKeys 列名解析金蝶 ExecuteBillQuery 行。
 */
public final class MesKingdeeRow {

    private final Map<String, String> values = new LinkedHashMap<>();

    private MesKingdeeRow() {
    }

    public static MesKingdeeRow parse(String fieldKeys, List<String> row) {
        MesKingdeeRow parsed = new MesKingdeeRow();
        if (!StringUtils.hasText(fieldKeys) || row == null) {
            return parsed;
        }
        String[] keys = fieldKeys.split(",");
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i].trim();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String val = i < row.size() && row.get(i) != null ? row.get(i).trim() : "";
            parsed.values.put(normalize(key), val);
        }
        return parsed;
    }

    public String get(String... aliases) {
        if (aliases == null) {
            return "";
        }
        for (String alias : aliases) {
            if (!StringUtils.hasText(alias)) {
                continue;
            }
            String value = values.get(normalize(alias));
            if (StringUtils.hasText(value) && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }

    public BigDecimal decimal(String... aliases) {
        String value = get(aliases);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Long longVal(String... aliases) {
        String value = get(aliases);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.split("\\.")[0]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Integer intVal(String... aliases) {
        Long value = longVal(aliases);
        return value == null ? null : value.intValue();
    }

    public LocalDate date(String... aliases) {
        String value = get(aliases);
        if (!StringUtils.hasText(value) || value.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public Integer flagOrDefault(int defaultValue, String... aliases) {
        String value = get(aliases);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        if ("1".equals(value) || "true".equalsIgnoreCase(value) || "Y".equalsIgnoreCase(value)) {
            return 1;
        }
        if ("0".equals(value) || "false".equalsIgnoreCase(value) || "N".equalsIgnoreCase(value)) {
            return 0;
        }
        return defaultValue;
    }

    public boolean isActive(boolean approvedOnly) {
        String forbid = get("FForbidStatus");
        String doc = get("FDocumentStatus");
        if (approvedOnly) {
            boolean notForbidden = !StringUtils.hasText(forbid) || "A".equalsIgnoreCase(forbid);
            boolean approved = !StringUtils.hasText(doc) || "C".equalsIgnoreCase(doc);
            return notForbidden && approved;
        }
        return !"B".equalsIgnoreCase(forbid);
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
