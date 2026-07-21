package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * 将金蝶 WebAPI 错误 JSON 转为可读提示。
 */
public final class KingdeeResponseMessageFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KingdeeResponseMessageFormatter() {
    }

    public static String format(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "金蝶同步失败";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            try {
                JsonNode array = MAPPER.readTree(trimmed);
                if (array.isArray() && !array.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode item : array) {
                        String msg = item.path("Message").asText("").trim();
                        if (!StringUtils.hasText(msg)) {
                            continue;
                        }
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(msg.replace("\r\n", " ").replace('\n', ' ').trim());
                    }
                    if (sb.length() > 0) {
                        return sb.toString();
                    }
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (trimmed.startsWith("{")) {
            try {
                JsonNode obj = MAPPER.readTree(trimmed);
                JsonNode errors = obj.path("Errors");
                if (errors.isArray() && !errors.isEmpty()) {
                    return format(errors.toString());
                }
                String msg = obj.path("Message").asText("").trim();
                if (StringUtils.hasText(msg)) {
                    return msg.replace("\r\n", " ").replace('\n', ' ').trim();
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "..." : trimmed;
    }
}
