package com.wms.integration.kingdee;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wms.common.cache.PdaShortCache;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.config.WmsPdaProperties;
import com.wms.integration.kingdee.dto.KingdeeStockCountBillVo;
import com.wms.integration.kingdee.dto.KingdeeStockCountLineVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 金蝶云星空 - 物料盘点作业（STK_StockCountInput，由盘点方案生成）。
 * 列表拉取未审核单据（FDocumentStatus in ('A','B')），PDA 实盘后回写并提交审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KingdeeStockCountService {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties properties;
    private final WmsPdaProperties pdaProperties;
    private final PdaShortCache pdaShortCache;

    public boolean isKingdeeEnabled() {
        return kingdeeCloudService.isEnabled();
    }

    public PageResult<KingdeeStockCountBillVo> pageBills(String keyword, long current, long size) {
        if (kingdeeCloudService.isEnabled()) {
            return pageFromKingdee(keyword, current, size);
        }
        if (!kingdeeCloudService.isMockEnabled()) {
            return PageResult.of(List.of(), 0, current, size);
        }
        return pageMock(keyword, current, size);
    }

    public KingdeeStockCountBillVo getBill(String billNo) {
        if (!StringUtils.hasText(billNo)) {
            return null;
        }
        String no = billNo.trim();
        if (kingdeeCloudService.isEnabled()) {
            return getFromKingdee(no);
        }
        if (!kingdeeCloudService.isMockEnabled()) {
            return null;
        }
        return getMock(no);
    }

    /**
     * 解析盘点二维码中的单据编号。支持 JSON、PD:/SC: 前缀或纯单号。
     */
    public String parseBillNo(String barcode) {
        if (!StringUtils.hasText(barcode)) {
            return "";
        }
        String raw = barcode.trim();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(raw);
            if (node.has("billNo")) {
                return node.get("billNo").asText("").trim();
            }
            if (node.has("taskNo")) {
                return node.get("taskNo").asText("").trim();
            }
            if (node.has("orderNo")) {
                return node.get("orderNo").asText("").trim();
            }
        } catch (Exception ignored) {
            // not json
        }
        if (raw.regionMatches(true, 0, "PD:", 0, 3)
                || raw.regionMatches(true, 0, "SC:", 0, 3)
                || raw.regionMatches(true, 0, "STOCKCHECK:", 0, 11)) {
            return raw.replaceFirst("(?i)^(PD|SC|STOCKCHECK):", "").trim();
        }
        var matcher = java.util.regex.Pattern.compile("(?i)(PD\\d{6,}|SC\\d{6,}|WLPD\\d{6,})").matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return raw.trim();
    }

    private PageResult<KingdeeStockCountBillVo> pageFromKingdee(String keyword, long current, long size) {
        String kwKey = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : "";
        String cacheKey = "kd-stockcount-list:" + kwKey;
        List<KingdeeStockCountBillVo> all = null;
        if (pdaProperties.getShortCacheTtlSeconds() > 0) {
            all = pdaShortCache.get(cacheKey, new TypeReference<List<KingdeeStockCountBillVo>>() {});
        }
        if (all == null) {
            long t0 = System.currentTimeMillis();
            all = queryUnauditedBills(keyword);
            log.info("Kingdee stock count list: bills={} costMs={}",
                    all.size(), System.currentTimeMillis() - t0);
            if (pdaProperties.getShortCacheTtlSeconds() > 0) {
                pdaShortCache.put(cacheKey, all,
                        Duration.ofSeconds(Math.max(60, pdaProperties.getShortCacheTtlSeconds())));
            }
        }
        int from = (int) Math.max(0, (current - 1) * size);
        int to = (int) Math.min(all.size(), from + size);
        List<KingdeeStockCountBillVo> slice = from < all.size() ? all.subList(from, to) : List.of();
        return PageResult.of(slice, all.size(), current, size);
    }

    private List<KingdeeStockCountBillVo> queryUnauditedBills(String keyword) {
        List<List<String>> detailRows = kingdeeCloudService.executeBillQuery(
                properties.getStockCountFormId(),
                properties.getStockCountDetailFieldKeys(),
                buildUnauditedFilter(keyword),
                "FDate desc, FBillNo desc",
                0,
                resolveQueryLimit());
        Map<String, KingdeeStockCountBillVo> fromDetail = buildBillsFromDetailRows(detailRows);
        if (!fromDetail.isEmpty()) {
            return fromDetail.values().stream()
                    .sorted(billNewestFirst())
                    .collect(Collectors.toList());
        }
        log.warn("Kingdee stock count detail query empty, fallback header list");
        List<List<String>> headerRows = kingdeeCloudService.executeBillQuery(
                properties.getStockCountFormId(),
                properties.getStockCountListFieldKeys(),
                buildUnauditedFilter(keyword),
                "FDate desc, FBillNo desc",
                0,
                resolveQueryLimit());
        Map<String, KingdeeStockCountBillVo> headers = new LinkedHashMap<>();
        for (List<String> row : headerRows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String billNo = cell(row, 0);
            if (!StringUtils.hasText(billNo)) {
                continue;
            }
            String no = billNo.trim();
            headers.putIfAbsent(no, KingdeeStockCountBillVo.builder()
                    .billNo(no)
                    .documentStatus(defaultStatus(cell(row, 1)))
                    .billDate(parseDate(cell(row, 2)))
                    .stockOrgCode(cell(row, 3))
                    .remark(cell(row, 4))
                    .totalLines(0)
                    .lines(new ArrayList<>())
                    .build());
        }
        return headers.values().stream().sorted(billNewestFirst()).collect(Collectors.toList());
    }

    private KingdeeStockCountBillVo getFromKingdee(String billNo) {
        // 按单号精确查，不限状态；业务层再校验是否允许盘点
        String filter = "FBillNo='" + escapeFilter(billNo) + "'";
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                properties.getStockCountFormId(),
                properties.getStockCountDetailFieldKeys(),
                filter,
                "FBillEntry_FSeq",
                0,
                resolveQueryLimit());
        Map<String, KingdeeStockCountBillVo> bills = buildBillsFromDetailRows(rows);
        KingdeeStockCountBillVo bill = bills.get(billNo);
        if (bill != null) {
            return bill;
        }
        bill = bills.values().stream()
                .filter(b -> billNo.equalsIgnoreCase(b.getBillNo()))
                .findFirst()
                .orElse(null);
        if (bill == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到盘点作业单: " + billNo);
        }
        return bill;
    }

    private Map<String, KingdeeStockCountBillVo> buildBillsFromDetailRows(List<List<String>> rows) {
        Map<String, KingdeeStockCountBillVo> bills = new LinkedHashMap<>();
        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String billNo = cell(row, 0);
            if (!StringUtils.hasText(billNo)) {
                continue;
            }
            String no = billNo.trim();
            String status = defaultStatus(cell(row, 2));
            KingdeeStockCountBillVo bill = bills.computeIfAbsent(no, k -> KingdeeStockCountBillVo.builder()
                    .billNo(no)
                    .billId(parseLong(cell(row, 1)))
                    .documentStatus(status)
                    .billDate(parseDate(cell(row, 3)))
                    .warehouseCode(cell(row, 4))
                    .lines(new ArrayList<>())
                    .build());
            if (bill.getBillId() == null) {
                bill.setBillId(parseLong(cell(row, 1)));
            }
            if (!StringUtils.hasText(bill.getWarehouseCode()) && StringUtils.hasText(cell(row, 4))) {
                bill.setWarehouseCode(cell(row, 4));
            }
            String materialCode = cell(row, 5);
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            int seq = parseInt(cell(row, 13), bill.getLines().size() + 1);
            bill.getLines().add(KingdeeStockCountLineVo.builder()
                    .lineNo(seq > 0 ? seq : bill.getLines().size() + 1)
                    .entryId(parseLong(cell(row, 12)))
                    .materialCode(materialCode.trim())
                    .materialName(cell(row, 6))
                    .specification(cell(row, 7))
                    .warehouseCode(cell(row, 4))
                    .batchNo(cell(row, 10))
                    .unitCode(cell(row, 11))
                    .bookQty(parseDecimal(cell(row, 8)))
                    .countQty(parseDecimal(cell(row, 9)))
                    .build());
        }
        for (KingdeeStockCountBillVo bill : bills.values()) {
            bill.setTotalLines(bill.getLines() != null ? bill.getLines().size() : 0);
        }
        return bills;
    }

    /**
     * 未审核：创建(A) / 审核中(B)
     */
    private String buildUnauditedFilter(String keyword) {
        StringBuilder filter = new StringBuilder("FDocumentStatus in ('A','B')");
        if (properties.getStockCountListDays() > 0) {
            LocalDate from = LocalDate.now().minusDays(properties.getStockCountListDays());
            filter.append(" and FDate>='").append(from).append("'");
        }
        if (StringUtils.hasText(keyword)) {
            String kw = escapeFilter(keyword.trim());
            filter.append(" and FBillNo like '%").append(kw).append("%'");
        }
        return filter.toString();
    }

    private int resolveQueryLimit() {
        int limit = properties.getStockCountQueryLimit();
        if (limit <= 0) {
            return 2000;
        }
        return Math.min(limit, 2000);
    }

    private Comparator<KingdeeStockCountBillVo> billNewestFirst() {
        return Comparator
                .comparing(KingdeeStockCountBillVo::getBillDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(KingdeeStockCountBillVo::getBillNo, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private PageResult<KingdeeStockCountBillVo> pageMock(String keyword, long current, long size) {
        List<KingdeeStockCountBillVo> all = mockBills();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            all = all.stream()
                    .filter(b -> b.getBillNo().toLowerCase().contains(kw)
                            || (b.getRemark() != null && b.getRemark().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        int from = (int) Math.max(0, (current - 1) * size);
        int to = (int) Math.min(all.size(), from + size);
        List<KingdeeStockCountBillVo> slice = from < all.size() ? all.subList(from, to) : List.of();
        return PageResult.of(slice, all.size(), current, size);
    }

    private KingdeeStockCountBillVo getMock(String billNo) {
        return mockBills().stream()
                .filter(b -> billNo.equalsIgnoreCase(b.getBillNo()))
                .findFirst()
                .orElse(null);
    }

    private List<KingdeeStockCountBillVo> mockBills() {
        List<KingdeeStockCountLineVo> lines1 = List.of(
                KingdeeStockCountLineVo.builder()
                        .lineNo(1).entryId(1001L)
                        .materialCode("MAT-001").materialName("轴承座")
                        .specification("Φ50*30").warehouseCode("CK001")
                        .batchNo("B20250701").unitCode("Pcs")
                        .bookQty(new BigDecimal("100")).countQty(BigDecimal.ZERO)
                        .build(),
                KingdeeStockCountLineVo.builder()
                        .lineNo(2).entryId(1002L)
                        .materialCode("MAT-002").materialName("密封圈")
                        .specification("NBR-20").warehouseCode("CK001")
                        .batchNo("B20250702").unitCode("Pcs")
                        .bookQty(new BigDecimal("250")).countQty(BigDecimal.ZERO)
                        .build(),
                KingdeeStockCountLineVo.builder()
                        .lineNo(3).entryId(1003L)
                        .materialCode("MAT-003").materialName("紧固螺丝")
                        .specification("M8*20").warehouseCode("CK001")
                        .batchNo("-").unitCode("Pcs")
                        .bookQty(new BigDecimal("1000")).countQty(BigDecimal.ZERO)
                        .build()
        );
        List<KingdeeStockCountLineVo> lines2 = List.of(
                KingdeeStockCountLineVo.builder()
                        .lineNo(1).entryId(2001L)
                        .materialCode("MAT-010").materialName("电机总成")
                        .specification("1.5KW").warehouseCode("CK002")
                        .batchNo("LOT-A").unitCode("Pcs")
                        .bookQty(new BigDecimal("12")).countQty(BigDecimal.ZERO)
                        .build()
        );
        return List.of(
                KingdeeStockCountBillVo.builder()
                        .billNo("PD202507210001")
                        .billId(90001L)
                        .billDate(LocalDate.now().minusDays(1))
                        .documentStatus("A")
                        .stockOrgCode("100")
                        .warehouseCode("CK001")
                        .remark("一号仓月度盘点")
                        .totalLines(lines1.size())
                        .lines(new ArrayList<>(lines1))
                        .build(),
                KingdeeStockCountBillVo.builder()
                        .billNo("PD202507200002")
                        .billId(90002L)
                        .billDate(LocalDate.now().minusDays(2))
                        .documentStatus("B")
                        .stockOrgCode("100")
                        .warehouseCode("CK002")
                        .remark("成品仓抽盘")
                        .totalLines(lines2.size())
                        .lines(new ArrayList<>(lines2))
                        .build()
        );
    }

    private static String cell(List<String> row, int index) {
        if (row == null || index < 0 || index >= row.size()) {
            return "";
        }
        String v = row.get(index);
        return v == null ? "" : v.trim();
    }

    private static String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : "A";
    }

    private static String escapeFilter(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        if (text.length() >= 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            try {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy/M/d"));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            String t = raw.trim();
            int dot = t.indexOf('.');
            if (dot > 0) {
                t = t.substring(0, dot);
            }
            return Long.parseLong(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String raw, int defaultVal) {
        if (!StringUtils.hasText(raw)) {
            return defaultVal;
        }
        try {
            String t = raw.trim();
            int dot = t.indexOf('.');
            if (dot > 0) {
                t = t.substring(0, dot);
            }
            return Integer.parseInt(t);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
