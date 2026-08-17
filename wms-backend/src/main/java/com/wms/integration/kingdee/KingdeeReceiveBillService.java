package com.wms.integration.kingdee;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.wms.common.cache.PdaShortCache;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.config.WmsPdaProperties;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillLineVo;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 金蝶云星空企业版 - 收料通知单（PUR_ReceiveBill）
 * 列表通过 ExecuteBillQuery 拉取；明细优先 View(Number=单号)，失败时回退 ExecuteBillQuery。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KingdeeReceiveBillService {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties properties;
    private final WmsPdaProperties pdaProperties;
    private final PdaShortCache pdaShortCache;

    public PageResult<KingdeeReceiveBillVo> pageBills(String keyword, long current, long size) {
        if (kingdeeCloudService.isEnabled()) {
            return pageFromKingdee(keyword, current, size);
        }
        return pageMock(keyword, current, size);
    }

    public boolean isKingdeeEnabled() {
        return kingdeeCloudService.isEnabled();
    }

    public KingdeeReceiveBillVo getBill(String billNo) {
        if (!StringUtils.hasText(billNo)) {
            return null;
        }
        if (kingdeeCloudService.isEnabled()) {
            return getFromKingdee(billNo.trim());
        }
        return getMock(billNo.trim());
    }

    /**
     * 按收料通知单单号查询物料明细（金蝶 View 接口，请求体 Number=单号）。
     * 解析 PUR_ReceiveEntry / FDetailEntity 中的物料编码、描述、单位、实收数量等。
     */
    public List<KingdeeReceiveBillLineVo> listBillMaterials(String billNo) {
        if (!StringUtils.hasText(billNo)) {
            return List.of();
        }
        String no = billNo.trim();
        if (!kingdeeCloudService.isEnabled()) {
            KingdeeReceiveBillVo mock = getMock(no);
            return mock != null && mock.getLines() != null ? mock.getLines() : List.of();
        }
        List<KingdeeReceiveBillLineVo> lines = pullMaterialsFromView(no);
        if (!lines.isEmpty()) {
            return lines;
        }
        log.warn("Kingdee View empty materials billNo={}, fallback ExecuteBillQuery", no);
        KingdeeReceiveBillVo fallback = pullBillWithLinesFromKingdee(no);
        return fallback != null && fallback.getLines() != null ? fallback.getLines() : List.of();
    }

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
            if (node.has("orderNo")) {
                return node.get("orderNo").asText("").trim();
            }
        } catch (Exception ignored) {
            // not json
        }
        if (raw.regionMatches(true, 0, "RN:", 0, 3) || raw.regionMatches(true, 0, "SLD:", 0, 4)) {
            return raw.replaceFirst("(?i)^(RN|SLD):", "").trim().toUpperCase();
        }
        var matcher = java.util.regex.Pattern.compile("(?i)(SLD\\d{6,}|RN\\d{6,})").matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        if (raw.matches("(?i)(SLD|RN)\\d{6,}")) {
            return raw.toUpperCase();
        }
        return raw.trim();
    }

    private PageResult<KingdeeReceiveBillVo> pageFromKingdee(String keyword, long current, long size) {
        String kwKey = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : "";
        String cacheKey = "kd-recv-list:" + kwKey;
        List<KingdeeReceiveBillVo> all = null;
        if (pdaProperties.getShortCacheTtlSeconds() > 0) {
            all = pdaShortCache.get(cacheKey, new TypeReference<List<KingdeeReceiveBillVo>>() {});
            if (all != null && all.isEmpty()) {
                all = null;
                pdaShortCache.evict(cacheKey);
            }
        }
        if (all == null) {
            long t0 = System.currentTimeMillis();
            all = pageFromKingdeeWithInspection(keyword);
            log.info("Kingdee receive bill list: distinctBills={} costMs={}",
                    all.size(), System.currentTimeMillis() - t0);
            if (pdaProperties.getShortCacheTtlSeconds() > 0 && !all.isEmpty()) {
                pdaShortCache.put(cacheKey, all,
                        Duration.ofSeconds(Math.max(60, pdaProperties.getShortCacheTtlSeconds())));
            }
        }
        int from = (int) Math.max(0, (current - 1) * size);
        int to = (int) Math.min(all.size(), from + size);
        List<KingdeeReceiveBillVo> slice = from < all.size() ? all.subList(from, to) : List.of();
        return PageResult.of(slice, all.size(), current, size);
    }

    private List<KingdeeReceiveBillVo> pageFromKingdeeWithInspection(String keyword) {
        int limit = resolveListQueryLimit();
        String fullKeys = properties.getReceiveBillInspectionListFieldKeys();
        String noRemainKeys = properties.getReceiveBillInspectionListFieldKeysNoRemain();

        InspectionQueryResult full = queryInspectionEligible(keyword, fullKeys, limit);
        if (full.isFailed()) {
            log.warn("Kingdee receive bill inspection query/parse failed with remain fields, retry without remain keys");
            InspectionQueryResult retry = queryInspectionEligible(keyword, noRemainKeys, limit);
            if (!retry.isFailed()) {
                return retry.bills();
            }
            log.error("Kingdee receive bill inspection list failed after retry; return empty to avoid unfiltered approved dump");
            return List.of();
        }

        // 带余量字段「成功但 0 行」：可能是字段非法被金蝶空返回，探测无余量 FieldKeys
        if (full.rawRowCount() == 0) {
            InspectionQueryResult probe = queryInspectionEligible(keyword, noRemainKeys, limit);
            if (!probe.isFailed() && probe.rawRowCount() > 0) {
                log.warn("Kingdee inspection full-keys empty but no-remain keys returned rawRows={}, use probe result",
                        probe.rawRowCount());
                return probe.bills();
            }
            log.info("Kingdee receive bill list truly empty after probe, keyword={}", keyword);
        }
        return full.bills();
    }

    /**
     * @return isFailed=true 表示查询/解析失败需换 FieldKeys；否则 bills 非 null（可为 empty）
     */
    private InspectionQueryResult queryInspectionEligible(String keyword, String fieldKeys, int limit) {
        List<List<String>> rows;
        try {
            rows = kingdeeCloudService.executeBillQuery(
                    properties.getReceiveBillFormId(),
                    fieldKeys,
                    buildApprovedFilter(keyword),
                    "FDate desc, FBillNo desc",
                    0,
                    limit);
        } catch (Exception e) {
            log.warn("Kingdee inspection ExecuteBillQuery failed fieldKeysLen={}",
                    fieldKeys != null ? fieldKeys.length() : 0, e);
            return InspectionQueryResult.failure();
        }
        if (rows == null) {
            rows = List.of();
        }
        List<KingdeeReceiveBillInspectionLine> inspectionRows = new ArrayList<>();
        for (List<String> row : rows) {
            KingdeeReceiveBillInspectionLine line = mapInspectionRow(row);
            if (line != null) {
                inspectionRows.add(line);
            }
        }
        if (!rows.isEmpty() && inspectionRows.isEmpty()) {
            List<String> sample = rows.get(0);
            log.warn("Kingdee inspection row parse failed, first rawRow cols={} data={}",
                    sample.size(), sample);
            return InspectionQueryResult.failure();
        }
        Map<String, Integer> lineCounts = countLinesPerBill(inspectionRows);
        List<KingdeeReceiveBillVo> eligible = KingdeeReceiveBillInspectionFilter.filterEligibleBills(inspectionRows).stream()
                .map(line -> toListBillFromInspection(line, resolveMaterialLineCount(line, lineCounts)))
                .sorted(billNewestFirst())
                .collect(Collectors.toList());
        log.info("Kingdee receive bill list: rawRows={}, parsedLines={}, afterInspectionFilter={}, limit={}",
                rows.size(), inspectionRows.size(), eligible.size(), limit);
        return InspectionQueryResult.ok(eligible, rows.size(), inspectionRows.size());
    }

    private record InspectionQueryResult(List<KingdeeReceiveBillVo> bills, int rawRowCount, int parsedLineCount) {
        static InspectionQueryResult failure() {
            return new InspectionQueryResult(null, -1, -1);
        }

        static InspectionQueryResult ok(List<KingdeeReceiveBillVo> bills, int rawRowCount, int parsedLineCount) {
            return new InspectionQueryResult(bills == null ? List.of() : bills, rawRowCount, parsedLineCount);
        }

        boolean isFailed() {
            return bills == null;
        }
    }

    private Map<String, Integer> countLinesPerBill(List<KingdeeReceiveBillInspectionLine> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (KingdeeReceiveBillInspectionLine row : rows) {
            if (row == null || !StringUtils.hasText(row.getBillNo())) {
                continue;
            }
            String no = row.getBillNo().trim();
            counts.merge(no, 1, Integer::sum);
        }
        return counts;
    }

    private int resolveMaterialLineCount(KingdeeReceiveBillInspectionLine line, Map<String, Integer> lineCounts) {
        if (line == null || !StringUtils.hasText(line.getBillNo())) {
            return 0;
        }
        if (line.getMaterialLineCount() != null && line.getMaterialLineCount() > 0) {
            return line.getMaterialLineCount();
        }
        return lineCounts.getOrDefault(line.getBillNo().trim(), 0);
    }

    private Comparator<KingdeeReceiveBillVo> billNewestFirst() {
        return Comparator
                .comparing(KingdeeReceiveBillVo::getBillDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(KingdeeReceiveBillVo::getBillNo, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private KingdeeReceiveBillInspectionLine mapInspectionRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        String billNo = cell(row, 0);
        if (!StringUtils.hasText(billNo)) {
            return null;
        }
        // 单据状态已在 buildApprovedFilter(FDocumentStatus='C') 中过滤，此处不再二次校验
        String status = cell(row, 3);
        return KingdeeReceiveBillInspectionLine.builder()
                .billNo(billNo.trim())
                .supplierCode(cell(row, 1))
                .supplierName(cell(row, 2))
                .documentStatus(StringUtils.hasText(status) ? status.trim() : "C")
                .billDate(parseDate(cell(row, 4)))
                .checkIncoming(KingdeeReceiveBillFieldParser.parseCheckIncoming(cell(row, 5)))
                .receiveQty(parseDecimal(cell(row, 6)))       // FDetailEntity.FActReceiveQty
                .checkQty(parseDecimal(cell(row, 7)))         // FDetailEntity.FCheckBaseQty
                .refuseQty(parseDecimal(cell(row, 8)))        // FDetailEntity.FRefuseBaseQty
                .qualifiedQty(parseDecimal(cell(row, 9)))     // FDetailEntity.FReceiveBaseQty
                .sampleDamageQty(parseDecimal(cell(row, 10)))  // FDetailEntity.FSampleDamageBaseQty
                .concessionQty(parseDecimal(cell(row, 11)))    // FDetailEntity.FCsnReceiveBaseQty
                .procScrapQty(parseDecimal(cell(row, 12)))    // FDetailEntity.FProcScrapBaseQty
                .mtrlScrapQty(parseDecimal(cell(row, 13)))    // FDetailEntity.FMtrlScrapBaseQty
                .inStockJoinBaseQty(parseDecimal(cell(row, 14)))
                .remainInStockBaseQty(parseDecimal(cell(row, 15)))
                .build();
    }

    private KingdeeReceiveBillVo toListBillFromInspection(KingdeeReceiveBillInspectionLine line, int materialLineCount) {
        return KingdeeReceiveBillVo.builder()
                .billNo(line.getBillNo())
                .supplierCode(line.getSupplierCode())
                .supplierName(line.getSupplierName())
                .documentStatus(line.getDocumentStatus())
                .billDate(line.getBillDate())
                .warehouseCode("WH01")
                .totalLines(materialLineCount)
                .build();
    }

    /** 已审核收料通知单：FDocumentStatus='C'；receiveBillListDays>0 时追加近 N 天 */
    private String buildApprovedFilter(String keyword) {
        String filter = "FDocumentStatus='C'";
        int days = properties.getReceiveBillListDays();
        if (days > 0) {
            LocalDate from = LocalDate.now().minusDays(days);
            filter += " and FDate>='" + from + "'";
        }
        if (StringUtils.hasText(keyword)) {
            String kw = escapeFilter(keyword);
            filter += " and (FBillNo like '%" + kw + "%' or FSupplierId.FName like '%" + kw + "%')";
        }
        return filter;
    }

    /** 列表查询 Limit：分录行上限，过小会导致可翻页单据不足 */
    private int resolveListQueryLimit() {
        int configured = properties.getReceiveBillListQueryLimit();
        if (configured <= 0) {
            return 2000;
        }
        return Math.min(configured, 5000);
    }

    private String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }

    private KingdeeReceiveBillVo getFromKingdee(String billNo) {
        KingdeeReceiveBillVo bill = pullBillFromView(billNo);
        if (bill != null && bill.getLines() != null && !bill.getLines().isEmpty()) {
            log.info("Kingdee View pulled bill {} lines={}", billNo, bill.getLines().size());
            return bill;
        }
        bill = pullBillWithLinesFromKingdee(billNo);
        if (bill != null) {
            log.info("Kingdee query pulled bill {} lines={}", billNo,
                    bill.getLines() != null ? bill.getLines().size() : 0);
        } else {
            log.warn("Kingdee pull empty for billNo={}", billNo);
        }
        return bill;
    }

    private List<KingdeeReceiveBillLineVo> pullMaterialsFromView(String billNo) {
        try {
            JsonNode billNode = kingdeeCloudService.viewBill(properties.getReceiveBillFormId(), billNo);
            if (billNode == null) {
                return List.of();
            }
            return KingdeeReceiveBillViewParser.parseMaterialLines(billNode);
        } catch (BusinessException ex) {
            log.warn("Kingdee View materials failed billNo={}: {}", billNo, ex.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("Kingdee View materials failed billNo={}", billNo, e);
            return List.of();
        }
    }

    private KingdeeReceiveBillVo pullBillFromView(String billNo) {
        try {
            JsonNode billNode = kingdeeCloudService.viewBill(properties.getReceiveBillFormId(), billNo);
            if (billNode == null) {
                return null;
            }
            KingdeeReceiveBillVo bill = KingdeeReceiveBillViewParser.parse(billNode);
            if (bill == null) {
                return null;
            }
            if (bill.getLines() == null || bill.getLines().isEmpty()) {
                List<KingdeeReceiveBillLineVo> lines = KingdeeReceiveBillViewParser.parseMaterialLines(billNode);
                bill.setLines(lines);
                bill.setTotalLines(lines.size());
            }
            return bill;
        } catch (BusinessException ex) {
            log.warn("Kingdee View bill failed billNo={}: {}", billNo, ex.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Kingdee View bill failed billNo={}", billNo, e);
            return null;
        }
    }

    /**
     * 从金蝶 ExecuteBillQuery 批量结果中解析单据+明细（不按单号调 View）。
     */
    private KingdeeReceiveBillVo pullBillWithLinesFromKingdee(String billNo) {
        String filter = buildApprovedFilter(null) + " and FBillNo='" + escapeFilter(billNo) + "'";
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                properties.getReceiveBillFormId(),
                properties.getReceiveBillDetailFieldKeys(),
                filter,
                "FBillNo",
                0,
                properties.getReceiveBillQueryLimit());
        Map<String, KingdeeReceiveBillVo> bills = buildBillFromDetailRows(rows);
        KingdeeReceiveBillVo bill = bills.get(billNo);
        if (bill != null) {
            return bill;
        }
        return bills.values().stream()
                .filter(b -> billNo.equalsIgnoreCase(b.getBillNo()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, KingdeeReceiveBillVo> buildBillFromDetailRows(List<List<String>> rows) {
        Map<String, KingdeeReceiveBillVo> bills = new LinkedHashMap<>();
        for (List<String> row : rows) {
            if (row == null || row.size() < 8) {
                continue;
            }
            String billNo = KingdeeReceiveBillDetailRowParser.billNo(row);
            if (!StringUtils.hasText(billNo)) {
                continue;
            }
            KingdeeReceiveBillVo bill = bills.computeIfAbsent(billNo, k -> KingdeeReceiveBillVo.builder()
                    .billNo(billNo)
                    .billId(KingdeeReceiveBillDetailRowParser.billId(row))
                    .billDate(KingdeeReceiveBillDetailRowParser.billDate(row))
                    .supplierCode(KingdeeReceiveBillDetailRowParser.supplierCode(row))
                    .supplierName(KingdeeReceiveBillDetailRowParser.supplierName(row))
                    .warehouseCode(StringUtils.hasText(KingdeeReceiveBillDetailRowParser.warehouseCode(row))
                            ? KingdeeReceiveBillDetailRowParser.warehouseCode(row) : "WH01")
                    .sendBillNo(blankToNull(KingdeeReceiveBillDetailRowParser.sendBillNo(row)))
                    .lines(new ArrayList<>())
                    .build());
            if (bill.getBillId() == null) {
                bill.setBillId(KingdeeReceiveBillDetailRowParser.billId(row));
            }
            if (!StringUtils.hasText(bill.getSendBillNo())) {
                bill.setSendBillNo(blankToNull(KingdeeReceiveBillDetailRowParser.sendBillNo(row)));
            }
            bill.getLines().add(KingdeeReceiveBillDetailRowParser.mapLine(row, bill.getLines().size() + 1));
        }
        for (KingdeeReceiveBillVo bill : bills.values()) {
            bill.setTotalLines(bill.getLines().size());
            if (StringUtils.hasText(bill.getSendBillNo()) && bill.getLines() != null) {
                for (KingdeeReceiveBillLineVo line : bill.getLines()) {
                    if (line != null && !StringUtils.hasText(line.getSendBillNo())) {
                        line.setSendBillNo(bill.getSendBillNo());
                    }
                }
            }
        }
        return bills;
    }

    private KingdeeReceiveBillLineVo mapDetailLine(List<String> row, int fallbackSeq) {
        return KingdeeReceiveBillDetailRowParser.mapLine(row, fallbackSeq);
    }

    private PageResult<KingdeeReceiveBillVo> pageMock(String keyword, long current, long size) {
        List<KingdeeReceiveBillVo> all = mockBills();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            all = all.stream()
                    .filter(b -> b.getBillNo().toLowerCase().contains(kw)
                            || (b.getSupplierName() != null && b.getSupplierName().toLowerCase().contains(kw))
                            || (b.getSupplierCode() != null && b.getSupplierCode().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        int from = (int) Math.max(0, (current - 1) * size);
        int to = (int) Math.min(all.size(), from + size);
        List<KingdeeReceiveBillVo> slice = from < all.size() ? all.subList(from, to) : List.of();
        return PageResult.of(slice, all.size(), current, size);
    }

    private KingdeeReceiveBillVo getMock(String billNo) {
        return mockBills().stream()
                .filter(b -> billNo.equalsIgnoreCase(b.getBillNo()))
                .findFirst()
                .map(this::withLines)
                .orElse(null);
    }

    private KingdeeReceiveBillVo withLines(KingdeeReceiveBillVo bill) {
        List<KingdeeReceiveBillLineVo> lines = mockLines(bill.getBillNo());
        bill.setLines(lines);
        bill.setTotalLines(lines.size());
        return bill;
    }

    private List<KingdeeReceiveBillVo> mockBills() {
        List<KingdeeReceiveBillVo> list = new ArrayList<>();
        list.add(KingdeeReceiveBillVo.builder()
                .billNo("SLD20260706001")
                .billDate(LocalDate.of(2026, 7, 6))
                .supplierCode("SUP001")
                .supplierName("华东供应商")
                .documentStatus("B")
                .warehouseCode("WH01")
                .totalLines(3)
                .build());
        list.add(KingdeeReceiveBillVo.builder()
                .billNo("SLD20260705002")
                .billDate(LocalDate.of(2026, 7, 5))
                .supplierCode("SUP002")
                .supplierName("华南供应商")
                .documentStatus("B")
                .warehouseCode("WH01")
                .totalLines(2)
                .build());
        list.add(KingdeeReceiveBillVo.builder()
                .billNo("SLD20260704003")
                .billDate(LocalDate.of(2026, 7, 4))
                .supplierCode("SUP003")
                .supplierName("北方供应商")
                .documentStatus("C")
                .warehouseCode("WH01")
                .totalLines(4)
                .build());
        return list;
    }

    private List<KingdeeReceiveBillLineVo> mockLines(String billNo) {
        Map<String, List<KingdeeReceiveBillLineVo>> map = new LinkedHashMap<>();
        map.put("SLD20260706001", List.of(
                line(1, "MAT-10001", "电阻 10K", "10KΩ ±1%", "B20260701", "PCS", "1000"),
                line(2, "MAT-10002", "电容 100uF", "100uF/25V", "B20260702", "PCS", "500"),
                line(3, "MAT-10003", "PCB主板", "V2.1-A", "B20260703", "PCS", "200")
        ));
        map.put("SLD20260705002", List.of(
                line(1, "MAT-20001", "连接器", "Type-C", "B20260628", "PCS", "300"),
                line(2, "MAT-20002", "屏蔽罩", "SUS304", "B20260629", "PCS", "300")
        ));
        map.put("SLD20260704003", List.of(
                line(1, "MAT-30001", "螺丝 M3", "M3*8", "B20260620", "PCS", "5000"),
                line(2, "MAT-30002", "垫片", "φ3", "B20260621", "PCS", "5000"),
                line(3, "MAT-30003", "包装盒", "200*150", "B20260622", "PCS", "800"),
                line(4, "MAT-30004", "标签纸", "100*60", "B20260623", "PCS", "800")
        ));
        return map.getOrDefault(billNo, List.of());
    }

    private KingdeeReceiveBillLineVo line(int no, String code, String name, String spec,
                                           String batch, String unit, String qty) {
        return KingdeeReceiveBillLineVo.builder()
                .lineNo(no)
                .materialCode(code)
                .materialName(name)
                .specification(spec)
                .batchNo(batch)
                .unitCode(unit)
                .planQty(new BigDecimal(qty))
                .receivedQty(BigDecimal.ZERO)
                .build();
    }

    private String escapeFilter(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    private LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, Math.min(10, s.length())));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String s) {
        try {
            return new BigDecimal(s == null || s.isBlank() ? "0" : s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s == null || s.isBlank() ? "0" : s.split("\\.")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    private Long parseLong(String s) {
        try {
            if (s == null || s.isBlank()) {
                return null;
            }
            return Long.parseLong(s.split("\\.")[0]);
        } catch (Exception e) {
            return null;
        }
    }
}
