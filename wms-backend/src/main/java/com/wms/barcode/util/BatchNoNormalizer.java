package com.wms.barcode.util;

import org.springframework.util.StringUtils;

/**
 * 批号规范化：修正扫码/金蝶带入的 {@code |批号|数量} 等异常格式。
 */
public final class BatchNoNormalizer {

    private BatchNoNormalizer() {
    }

    /**
     * 规范化批号；空或 DEFAULT 返回 null。
     */
    public static String normalize(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            return null;
        }
        String trimmed = batchNo.trim();
        if ("DEFAULT".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if (trimmed.startsWith("|") && trimmed.indexOf('|', 1) > 0) {
            String[] parts = trimmed.split("\\|", -1);
            if (parts.length >= 3 && StringUtils.hasText(parts[1])) {
                return parts[1].trim();
            }
        }
        return trimmed;
    }

    /**
     * 从 {@code 物料|批号|数量} 条码解析批号。
     */
    public static String parseFromPipeBarcode(String raw) {
        if (!StringUtils.hasText(raw) || !raw.contains("|")) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 2 || !StringUtils.hasText(parts[0])) {
            return null;
        }
        if (parts.length == 2) {
            String second = parts[1].trim();
            if (second.matches("\\d+(?:\\.\\d+)?")) {
                return null;
            }
            return normalize(second);
        }
        if (parts.length >= 3 && StringUtils.hasText(parts[1])) {
            return normalize(parts[1].trim());
        }
        return null;
    }

    public static boolean isMalformed(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            return false;
        }
        String trimmed = batchNo.trim();
        return trimmed.startsWith("|") && trimmed.indexOf('|', 1) > 0;
    }

    /**
     * 提交金蝶/WMS 库存使用的批号，无有效批号时返回 DEFAULT。
     */
    public static String forSubmit(String batchNo) {
        String normalized = normalize(batchNo);
        return StringUtils.hasText(normalized) ? normalized : "DEFAULT";
    }
}
