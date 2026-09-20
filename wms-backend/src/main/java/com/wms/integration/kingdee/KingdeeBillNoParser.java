package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

/**
 * 从 PDA 扫到的单据二维码/条码中提取金蝶单号。
 */
public final class KingdeeBillNoParser {

    private KingdeeBillNoParser() {
    }

    public static String parse(String barcode) {
        if (!StringUtils.hasText(barcode)) {
            return "";
        }
        String raw = barcode.trim()
                .replace("\uFEFF", "")
                .replaceAll("[\\r\\n\\t]", "");
        String fromJson = extractBillNoFromJson(raw);
        if (StringUtils.hasText(fromJson)) {
            return fromJson.trim().toUpperCase();
        }
        String fromUrl = extractBillNoFromUrl(raw);
        if (StringUtils.hasText(fromUrl)) {
            return fromUrl.trim().toUpperCase();
        }
        if (raw.regionMatches(true, 0, "RN:", 0, 3)
                || raw.regionMatches(true, 0, "SLD:", 0, 4)
                || raw.regionMatches(true, 0, "SCT:", 0, 4)
                || raw.regionMatches(true, 0, "SCL:", 0, 4)
                || raw.regionMatches(true, 0, "SCR:", 0, 4)
                || raw.regionMatches(true, 0, "STK:", 0, 4)
                || raw.regionMatches(true, 0, "SCB:", 0, 4)
                || raw.regionMatches(true, 0, "RETURN:", 0, 7)
                || raw.regionMatches(true, 0, "BILL:", 0, 5)
                || raw.regionMatches(true, 0, "PRD_INSTOCK:", 0, 12)
                || raw.regionMatches(true, 0, "PRD_INSTOCK=", 0, 12)) {
            return raw.replaceFirst("(?i)^(RN|SLD|SCT|SCL|SCR|STK|SCB|RETURN|BILL|PRD_INSTOCK)[:：=]", "")
                    .trim()
                    .toUpperCase();
        }
        if (raw.matches("(?i)[A-Z]{2,8}\\d{6,}")) {
            return raw.toUpperCase();
        }
        var matcher = java.util.regex.Pattern.compile("(?i)([A-Z]{2,8}\\d{6,})").matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return raw.trim().toUpperCase();
    }

    private static String extractBillNoFromJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String text = raw.trim();
        int brace = text.indexOf('{');
        if (brace < 0) {
            return null;
        }
        text = text.substring(brace);
        try {
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
            String found = findBillNoInJson(node);
            if (StringUtils.hasText(found)) {
                return found;
            }
        } catch (Exception ignored) {
            // not json
        }
        return null;
    }

    private static String findBillNoInJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            for (String key : new String[]{
                    "billNo", "BillNo", "FBillNo", "billno", "bill_no",
                    "orderNo", "OrderNo", "FNumber", "number", "Number",
                    "BillNO", "FBILLNO"
            }) {
                if (node.has(key) && StringUtils.hasText(node.get(key).asText())) {
                    return node.get(key).asText().trim();
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String nested = findBillNoInJson(entry.getValue());
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = findBillNoInJson(child);
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static String extractBillNoFromUrl(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (!raw.contains("=") && !raw.contains("?")) {
            return null;
        }
        var matcher = java.util.regex.Pattern.compile(
                "(?i)(?:billno|fbillno|orderno|number|bill_no)=([A-Za-z0-9_\\-]+)").matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
