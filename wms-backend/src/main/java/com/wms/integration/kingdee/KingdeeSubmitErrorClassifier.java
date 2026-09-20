package com.wms.integration.kingdee;

import org.springframework.util.StringUtils;

/**
 * 判定金蝶 Submit 失败信息的语义，决定是否可继续走审批。
 */
public final class KingdeeSubmitErrorClassifier {

    private static final String[] NOT_SUBMITTED_YET = {
            "请先提交", "尚未提交", "未提交", "先提交审核"
    };

    /** 明确表示单据已不在「可提交」状态，可继续 Audit */
    private static final String[] ALREADY_SUBMITTED = {
            "已提交", "已经提交", "重复提交", "审核中", "已审核", "工作流",
            // 金蝶标准提示：只有暂存/创建/重新审核才允许提交 → 当前已是提交态(B)
            "只有暂存", "才允许提交", "不允许提交", "不能提交", "不可提交"
    };

    private KingdeeSubmitErrorClassifier() {
    }

    /**
     * Submit 失败是否因为「单据此前已提交」，可继续审批。
     * <p>「请先提交审核」「尚未提交」表示单据仍是暂存态（多因分录必填项缺失导致提交不成功），
     * 必须按失败处理；否则会继续 Audit 并拼出「提交成功但审核失败:请先提交审核!」的误导性提示。
     */
    public static boolean isAlreadySubmitted(String submitError) {
        if (!StringUtils.hasText(submitError)) {
            return false;
        }
        String text = submitError.trim();
        for (String keyword : NOT_SUBMITTED_YET) {
            if (text.contains(keyword)) {
                return false;
            }
        }
        for (String keyword : ALREADY_SUBMITTED) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
