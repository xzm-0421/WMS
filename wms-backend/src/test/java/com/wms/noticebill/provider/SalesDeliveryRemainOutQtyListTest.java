package com.wms.noticebill.provider;

import com.wms.noticebill.NoticeBillType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDA 销售发货通知列表：只拉未出库数量不等于 0 的单据。
 */
class SalesDeliveryRemainOutQtyListTest {

    @Test
    @DisplayName("仅销售发货列表按未出库数量过滤")
    void onlySalesDeliveryUsesRemainOutQtyListFilter() {
        assertThat(NoticeBillType.SALES_DELIVERY.isOpenRemainOutQtyListBill()).isTrue();
        assertThat(NoticeBillType.SALES_RETURN.isOpenRemainOutQtyListBill()).isFalse();
        assertThat(NoticeBillType.PRODUCTION_ISSUE.isOpenRemainOutQtyListBill()).isFalse();
        assertThat(NoticeBillType.PURCHASE_RECEIVE.isOpenRemainOutQtyListBill()).isFalse();
    }

    @Test
    @DisplayName("未出库数量为 0 的分录不进入列表聚合")
    void zeroRemainOutQtyIsExcluded() {
        assertThat(KingdeeConfigurableNoticeBillService.isNonZeroRemainOutQty("0")).isFalse();
        assertThat(KingdeeConfigurableNoticeBillService.isNonZeroRemainOutQty("0.00")).isFalse();
        assertThat(KingdeeConfigurableNoticeBillService.isNonZeroRemainOutQty("1")).isTrue();
        assertThat(KingdeeConfigurableNoticeBillService.isNonZeroRemainOutQty("0.001")).isTrue();
        assertThat(KingdeeConfigurableNoticeBillService.isNonZeroRemainOutQty("")).isTrue();
        assertThat(KingdeeConfigurableNoticeBillService.isNonZeroRemainOutQty(null)).isTrue();
    }
}
