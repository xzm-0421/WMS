package com.wms.noticebill;

import com.wms.common.result.PageResult;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;

/**
 * ERP 通知单数据源（按单据类型）。
 */
public interface NoticeBillProvider {

    NoticeBillType type();

    PageResult<KingdeeReceiveBillVo> pageBills(String keyword, long current, long size);

    KingdeeReceiveBillVo getBill(String billNo);

    String parseBillNo(String barcode);

    boolean isErpEnabled();
}
