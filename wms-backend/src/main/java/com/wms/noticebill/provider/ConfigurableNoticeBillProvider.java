package com.wms.noticebill.provider;

import com.wms.common.result.PageResult;
import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;
import com.wms.noticebill.NoticeBillProvider;
import com.wms.noticebill.NoticeBillType;

public class ConfigurableNoticeBillProvider implements NoticeBillProvider {

    private final KingdeeConfigurableNoticeBillService configurableNoticeBillService;
    private final KingdeeCloudService kingdeeCloudService;
    private final NoticeBillType billType;

    public ConfigurableNoticeBillProvider(KingdeeConfigurableNoticeBillService service,
                                          KingdeeCloudService cloudService,
                                          NoticeBillType billType) {
        this.configurableNoticeBillService = service;
        this.kingdeeCloudService = cloudService;
        this.billType = billType;
    }

    @Override
    public NoticeBillType type() {
        return billType;
    }

    @Override
    public PageResult<KingdeeReceiveBillVo> pageBills(String keyword, long current, long size) {
        return configurableNoticeBillService.pageBills(billType, keyword, current, size);
    }

    @Override
    public KingdeeReceiveBillVo getBill(String billNo) {
        return configurableNoticeBillService.getBill(billType, billNo);
    }

    @Override
    public String parseBillNo(String barcode) {
        return configurableNoticeBillService.parseBillNo(barcode);
    }

    @Override
    public boolean isErpEnabled() {
        return kingdeeCloudService.isEnabled();
    }
}
