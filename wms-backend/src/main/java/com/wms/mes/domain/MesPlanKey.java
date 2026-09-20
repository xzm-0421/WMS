package com.wms.mes.domain;

import org.springframework.util.StringUtils;

/**
 * 工序计划本地主键：优先 ERP 单号+分录，否则工单+工序+行号。
 */
public final class MesPlanKey {

    private MesPlanKey() {
    }

    public static String of(String erpBillNo, Long erpEntryId, String moNo, String processCode, Integer seqNo) {
        if (StringUtils.hasText(erpBillNo) && erpEntryId != null && erpEntryId > 0) {
            return trim(erpBillNo) + "#" + erpEntryId;
        }
        if (StringUtils.hasText(erpBillNo) && StringUtils.hasText(processCode)) {
            return trim(erpBillNo) + "#" + trim(processCode);
        }
        String seq = seqNo == null ? "0" : String.valueOf(seqNo);
        return trim(moNo) + "#" + trim(processCode) + "#" + seq;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
