package com.wms.common.util;

import org.springframework.util.StringUtils;

/**
 * 业务编码自动生成：前缀 + 日期(yyyyMMdd) + 4位流水。
 */
public final class CodeAutoGenerator {

    private CodeAutoGenerator() {
    }

    public static String ensureOrGenerate(String code, String prefix) {
        if (StringUtils.hasText(code)) {
            return code.trim();
        }
        return OrderNoGenerator.next(prefix);
    }
}
