package com.wms.noticebill;

/**
 * 通知单扫码方向：入库增加库存，出库减少库存。
 */
public enum NoticeBillDirection {
    INBOUND,
    OUTBOUND;

    public boolean isInbound() {
        return this == INBOUND;
    }
}
