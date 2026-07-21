package com.wms.integration.kingdee;

import org.springframework.util.StringUtils;

/**
 * 金蝶收料通知单字段解析。
 */
public final class KingdeeReceiveBillFieldParser {

    private KingdeeReceiveBillFieldParser() {
    }

    /**
     * 来料检验标识 FCheckInComing：1/true=来料检验（显示），0/false=非来料检验（不显示）。
     */
    public static boolean parseCheckIncoming(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String v = raw.trim();
        if ("0".equals(v) || "false".equalsIgnoreCase(v) || "False".equals(v)) {
            return false;
        }
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "True".equals(v);
    }
}
