package com.wms.noticebill.provider;

import com.wms.common.result.PageResult;
import com.wms.integration.kingdee.KingdeeReceiveBillService;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;
import com.wms.noticebill.NoticeBillProvider;
import com.wms.noticebill.NoticeBillType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PurchaseReceiveNoticeProvider implements NoticeBillProvider {

    private final KingdeeReceiveBillService kingdeeReceiveBillService;

    @Override
    public NoticeBillType type() {
        return NoticeBillType.PURCHASE_RECEIVE;
    }

    @Override
    public PageResult<KingdeeReceiveBillVo> pageBills(String keyword, long current, long size) {
        return kingdeeReceiveBillService.pageBills(keyword, current, size);
    }

    @Override
    public KingdeeReceiveBillVo getBill(String billNo) {
        return kingdeeReceiveBillService.getBill(billNo);
    }

    @Override
    public String parseBillNo(String barcode) {
        return kingdeeReceiveBillService.parseBillNo(barcode);
    }

    @Override
    public boolean isErpEnabled() {
        return kingdeeReceiveBillService.isKingdeeEnabled();
    }
}
