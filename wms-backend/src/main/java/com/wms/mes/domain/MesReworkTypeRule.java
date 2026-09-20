package com.wms.mes.domain;

import com.wms.mes.MesConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 正常工序未完成可选「正常/返工」；完成后仅可返工报工。
 */
public final class MesReworkTypeRule {

    private MesReworkTypeRule() {
    }

    public static List<String> allowedReportTypes(boolean normalCompleted, boolean hasRework) {
        List<String> types = new ArrayList<>();
        if (!normalCompleted) {
            types.add(MesConstants.REPORT_NORMAL);
        }
        if (hasRework) {
            types.add(MesConstants.REPORT_REWORK);
        }
        if (types.isEmpty()) {
            types.add(MesConstants.REPORT_NORMAL);
        }
        return types;
    }

    public static boolean allowNormal(boolean normalCompleted) {
        return !normalCompleted;
    }

    public static String completedHint() {
        return "正常工序已完成，请进行返工报工。";
    }
}
