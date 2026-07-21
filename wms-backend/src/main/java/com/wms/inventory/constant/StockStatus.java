package com.wms.inventory.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 库存状态（与 inventory.stock_status 字段一致）。
 */
@Getter
public enum StockStatus {

    AVAILABLE("AVAILABLE", "可用"),
    FROZEN("FROZEN", "冻结"),
    INSPECTION("INSPECTION", "待检"),
    UNQUALIFIED("UNQUALIFIED", "不合格"),
    SCRAPPED("SCRAPPED", "报废");

    private final String code;
    private final String label;

    StockStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static Optional<StockStatus> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(s -> s.code.equals(normalized))
                .findFirst();
    }

    public static String labelOf(String code) {
        return fromCode(code).map(StockStatus::getLabel).orElse(code);
    }

    public static List<Map<String, String>> options() {
        return Arrays.stream(values())
                .map(s -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("value", s.code);
                    item.put("label", s.label);
                    return item;
                })
                .toList();
    }
}
