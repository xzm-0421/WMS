package com.wms.integration.kingdee;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 来料检验规则下的收料通知单列表过滤。
 */
public final class KingdeeReceiveBillInspectionFilter {

    private KingdeeReceiveBillInspectionFilter() {
    }

    /**
     * 保留至少有一行可显示明细分录的单据，并统计该单据物料行数。
     */
    public static List<KingdeeReceiveBillInspectionLine> filterEligibleBills(
            List<KingdeeReceiveBillInspectionLine> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, List<KingdeeReceiveBillInspectionLine>> grouped = rows.stream()
                .filter(r -> StringUtils.hasText(r.getBillNo()))
                .collect(Collectors.groupingBy(r -> r.getBillNo().trim()));
        return grouped.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(KingdeeReceiveBillInspectionFilter::isEligibleLine))
                .map(e -> {
                    List<KingdeeReceiveBillInspectionLine> lines = e.getValue();
                    KingdeeReceiveBillInspectionLine head = lines.stream()
                            .filter(KingdeeReceiveBillInspectionFilter::isEligibleLine)
                            .findFirst()
                            .orElse(lines.get(0));
                    int eligibleCount = (int) lines.stream()
                            .filter(KingdeeReceiveBillInspectionFilter::isEligibleLine)
                            .count();
                    head.setMaterialLineCount(eligibleCount);
                    return head;
                })
                .toList();
    }

    /**
     * 列表明细分录是否可显示：
     * <ul>
     *   <li>剩余可入库/可处理为 0 的行不显示</li>
     *   <li>来料检验=0（免检）：有余量即可显示</li>
     *   <li>来料检验=1：
     *     <ul>
     *       <li>显示：检验数 = 收料数，且均 &gt; 0</li>
     *       <li>不显示：检验数 = 判退数（全判退）</li>
     *       <li>另需合格数 &gt; 0（有可入库数量）</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    public static boolean isEligibleLine(KingdeeReceiveBillInspectionLine line) {
        if (line == null) {
            return false;
        }
        if (!hasInboundRemain(line)) {
            return false;
        }
        // 免检：不校验检验数/判退数
        if (!line.isCheckIncoming()) {
            return true;
        }

        BigDecimal receiveQty = nz(line.getReceiveQty());
        BigDecimal checkQty = nz(line.getCheckQty());
        BigDecimal refuseQty = nz(line.getRefuseQty());
        BigDecimal qualifiedQty = nz(line.getQualifiedQty());

        // 显示条件：检验数 = 收料数，且均 > 0
        if (checkQty.compareTo(BigDecimal.ZERO) <= 0 || receiveQty.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (!eq(checkQty, receiveQty)) {
            return false;
        }
        // 不显示：检验数 = 判退数（全判退）
        if (eq(checkQty, refuseQty)) {
            return false;
        }
        return qualifiedQty.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 是否仍有可入库余量。
     * 能算出余量则严格按余量；收料与合格均明确为 0 则视为无可处理；字段全缺时放行以免误杀整表。
     */
    static boolean hasInboundRemain(KingdeeReceiveBillInspectionLine line) {
        BigDecimal remain = resolveRemainQty(line);
        if (remain != null) {
            return remain.compareTo(BigDecimal.ZERO) > 0;
        }
        boolean hasReceiveField = line.getReceiveQty() != null;
        boolean hasQualifiedField = line.getQualifiedQty() != null;
        if (hasReceiveField || hasQualifiedField) {
            return nz(line.getReceiveQty()).compareTo(BigDecimal.ZERO) > 0
                    || nz(line.getQualifiedQty()).compareTo(BigDecimal.ZERO) > 0;
        }
        return true;
    }

    /**
     * 剩余可入库：与明细/提交共用 {@link KingdeeReceiveRemainQty}。
     * 无法判断时返回 null。
     */
    static BigDecimal resolveRemainQty(KingdeeReceiveBillInspectionLine line) {
        if (line == null) {
            return null;
        }
        return KingdeeReceiveRemainQty.resolve(
                line.getRemainInStockBaseQty(),
                line.getQualifiedQty(),
                line.getReceiveQty(),
                line.getInStockJoinBaseQty());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static boolean eq(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) == 0;
    }
}
