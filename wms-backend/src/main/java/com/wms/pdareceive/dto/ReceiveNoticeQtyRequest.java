package com.wms.pdareceive.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReceiveNoticeQtyRequest {

    /** 本次待提交领取数量（库存单位，非累计扫描量）。双单位主录件数时可与 auxQty 二选一传。 */
    private BigDecimal qty;

    /**
     * 本次待提交计价/辅助单位数量。
     * 双单位行可传；未传时按计划换算率自动计算。
     */
    private BigDecimal auxQty;
}
