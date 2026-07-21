package com.wms.integration.kingdee;

import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * 金蝶 ExecuteBillQuery FilterString 片段组装。
 */
public final class KingdeeBillQueryFilters {

    private KingdeeBillQueryFilters() {
    }

    /** 追加近 N 天单据日期条件（FDate，含当天）。days &lt;= 0 时不追加。 */
    public static String appendRecentDays(String filter, int days) {
        if (!StringUtils.hasText(filter) || days <= 0) {
            return filter;
        }
        LocalDate from = LocalDate.now().minusDays(days);
        return filter + " and FDate >= '" + from + "'";
    }
}
