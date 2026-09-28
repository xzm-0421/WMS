package com.wms.mes.domain;

import com.wms.mes.MesConstants;

/**
 * 返工不良单状态流转规则（全手动）。
 *
 * <pre>
 * REWORKING            --complete-->  DONE
 * REWORKING / DONE     --secondary--> SECONDARY
 * DONE / SECONDARY     --close-->     CLOSED
 * </pre>
 */
public final class MesReworkStatusRule {

    private MesReworkStatusRule() {
    }

    public static boolean canComplete(String current) {
        return MesConstants.REWORK_REWORKING.equals(current);
    }

    public static boolean canSecondary(String current) {
        return MesConstants.REWORK_REWORKING.equals(current)
                || MesConstants.REWORK_DONE.equals(current);
    }

    public static boolean canClose(String current) {
        return MesConstants.REWORK_DONE.equals(current)
                || MesConstants.REWORK_SECONDARY.equals(current);
    }
}
