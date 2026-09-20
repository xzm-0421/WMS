package com.wms.noticebill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产领料标准流程：扫码填数量 → 提交即回写实发并审核；反审核后单据回到审核中可重新领。
 */
class ProductionIssueStandardFlowTest {

    @Test
    @DisplayName("生产领料提交即审核，不等本单领满")
    void productionIssueAuditsOnEverySubmit() {
        assertThat(NoticeBillType.PRODUCTION_ISSUE.isSubmitThenAuditBill()).isTrue();
    }

    @Test
    @DisplayName("其它领退补单据仍按领满后审核，避免首批审核后无法继续改数")
    void otherPickBillsKeepFullySubmittedAudit() {
        assertThat(NoticeBillType.OUTSOURCE_ISSUE.isSubmitThenAuditBill()).isFalse();
        assertThat(NoticeBillType.PRODUCTION_FEED.isSubmitThenAuditBill()).isFalse();
        assertThat(NoticeBillType.OUTSOURCE_FEED.isSubmitThenAuditBill()).isFalse();
    }

    @Test
    @DisplayName("列表拉未审核单据（暂存/创建/审核中/重新审核）")
    void productionIssueListsSubmittedInProcessBillsOnly() {
        assertThat(NoticeBillType.PRODUCTION_ISSUE.isSubmittedInProcessListBill()).isTrue();
        assertThat(NoticeBillType.OUTSOURCE_ISSUE.isSubmittedInProcessListBill()).isFalse();
    }

    @Test
    @DisplayName("生产领料不改 WMS 库存，仅回写并驱动金蝶审核")
    void productionIssueConfirmsErpWithoutWmsStock() {
        assertThat(NoticeBillType.PRODUCTION_ISSUE.isErpConfirmWithoutWmsStock()).isTrue();
        assertThat(NoticeBillType.PRODUCTION_ISSUE.isWorkflowAuditBill()).isFalse();
    }
}
