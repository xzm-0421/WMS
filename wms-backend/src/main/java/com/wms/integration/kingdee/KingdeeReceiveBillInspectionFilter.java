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
                    KingdeeReceiveBillInspectionLine head = e.getValue().get(0);
                    int lineCount = e.getValue().size();
                    head.setMaterialLineCount(lineCount);
                    return head;
                })
                .toList();
    }

    /**
     * 来料检验明细分录可显示条件（须同时满足）：
     * 1. FCheckInComing=1（来料检验）才显示，=0 不显示
     * 2. 检验数量(FCheckBaseQty) = 收料数
     * 3. 检验数量 ≠ 判退数量
     * 4. 合格+判退+样本破坏+让步+工废+料废 ≠ 检验数量
     */
    public static boolean isEligibleLine(KingdeeReceiveBillInspectionLine line) {
        if (line == null) {
            return false;
        }
        if (!line.isCheckIncoming()) {
            return false;
        }

        BigDecimal receiveQty = nz(line.getReceiveQty());
        BigDecimal checkQty = nz(line.getCheckQty());
        BigDecimal refuseQty = nz(line.getRefuseQty());

        if (checkQty.compareTo(BigDecimal.ZERO) <= 0 || receiveQty.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (!eq(checkQty, receiveQty)) {
            return false;
        }
        if (eq(checkQty, refuseQty)) {
            return false;
        }

        BigDecimal dispositionSum = nz(line.getQualifiedQty())
                .add(nz(line.getRefuseQty()))
                .add(nz(line.getSampleDamageQty()))
                .add(nz(line.getConcessionQty()))
                .add(nz(line.getProcScrapQty()))
                .add(nz(line.getMtrlScrapQty()));
        return !eq(dispositionSum, checkQty);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static boolean eq(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) == 0;
    }
}
