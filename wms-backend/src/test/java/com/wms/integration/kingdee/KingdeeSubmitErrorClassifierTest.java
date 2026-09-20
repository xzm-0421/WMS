package com.wms.integration.kingdee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeSubmitErrorClassifierTest {

    @Test
    void treatsPleaseSubmitFirstAsNotSubmitted() {
        // 暂存单缺批号/仓库时提交失败，误判为已提交会继续审核并报「提交成功但审核失败:请先提交审核!」
        assertFalse(KingdeeSubmitErrorClassifier.isAlreadySubmitted("请先提交审核!"));
        assertFalse(KingdeeSubmitErrorClassifier.isAlreadySubmitted("单据尚未提交，不能审核"));
        assertFalse(KingdeeSubmitErrorClassifier.isAlreadySubmitted("当前单据未提交"));
    }

    @Test
    void treatsAlreadySubmittedAsResubmitted() {
        assertTrue(KingdeeSubmitErrorClassifier.isAlreadySubmitted("单据已提交，不能重复提交"));
        assertTrue(KingdeeSubmitErrorClassifier.isAlreadySubmitted("单据处于审核中状态"));
        assertTrue(KingdeeSubmitErrorClassifier.isAlreadySubmitted("已进入工作流审批"));
        // 采购退料等：已是提交态再 Submit 会报此文案
        assertTrue(KingdeeSubmitErrorClassifier.isAlreadySubmitted(
                "单据编号为“CGTL26090101”的采购退料单，只有暂存、创建和重新审核的数据才允许提交！"));
        assertTrue(KingdeeSubmitErrorClassifier.isAlreadySubmitted("当前状态不允许提交"));
    }

    @Test
    void blankErrorIsNotAlreadySubmitted() {
        assertFalse(KingdeeSubmitErrorClassifier.isAlreadySubmitted(null));
        assertFalse(KingdeeSubmitErrorClassifier.isAlreadySubmitted("  "));
        assertFalse(KingdeeSubmitErrorClassifier.isAlreadySubmitted("网络超时"));
    }
}
