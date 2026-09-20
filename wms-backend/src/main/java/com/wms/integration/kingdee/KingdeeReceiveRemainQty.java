package com.wms.integration.kingdee;

import java.math.BigDecimal;

/**
 * 收料通知单剩余可入库数量统一算法（列表过滤与明细/提交共用）。
 */
public final class KingdeeReceiveRemainQty {

    private KingdeeReceiveRemainQty() {
    }

    /**
     * 剩余可入库：优先 remainInStockBaseQty；否则 (合格数优先，否则收料数) - 已入库关联。
     * 无法判断时返回 null。
     *
     * @param remainInStockBaseQty 金蝶剩余可入库 FRemainInStockBaseQty，可为 null
     * @param qualifiedQty         合格数量 FReceiveBaseQty，可为 null
     * @param receiveQty           收料数 FActReceiveQty，可为 null
     * @param inStockJoinBaseQty   已入库关联 FInStockJoinBaseQty，可为 null
     */
    public static BigDecimal resolve(BigDecimal remainInStockBaseQty,
                                     BigDecimal qualifiedQty,
                                     BigDecimal receiveQty,
                                     BigDecimal inStockJoinBaseQty) {
        if (remainInStockBaseQty != null) {
            return remainInStockBaseQty.compareTo(BigDecimal.ZERO) < 0
                    ? BigDecimal.ZERO
                    : remainInStockBaseQty;
        }
        BigDecimal joined = nz(inStockJoinBaseQty);
        BigDecimal basis = nz(qualifiedQty);
        if (basis.compareTo(BigDecimal.ZERO) <= 0) {
            basis = nz(receiveQty);
        }
        if (basis.compareTo(BigDecimal.ZERO) <= 0 && joined.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal remain = basis.subtract(joined);
        return remain.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remain;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
