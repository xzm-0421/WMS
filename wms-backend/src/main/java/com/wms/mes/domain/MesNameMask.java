package com.wms.mes.domain;

import org.springframework.util.StringUtils;

/**
 * 列表页操作员姓名脱敏，例如「张*」。
 */
public final class MesNameMask {

    private MesNameMask() {
    }

    public static String mask(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return "*";
        }
        return trimmed.charAt(0) + "*";
    }
}
