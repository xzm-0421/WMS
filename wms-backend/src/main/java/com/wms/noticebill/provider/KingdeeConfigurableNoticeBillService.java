package com.wms.noticebill.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.common.result.PageResult;
import com.wms.config.WmsPdaProperties;
import com.wms.integration.kingdee.*;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillLineVo;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;
import com.wms.noticebill.NoticeBillType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 可配置 FormId 的金蝶通知单查询（生产入库、销售发货等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KingdeeConfigurableNoticeBillService {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties properties;
    private final WmsPdaProperties pdaProperties;

    public PageResult<KingdeeReceiveBillVo> pageBills(NoticeBillType billType, String keyword, long current, long size) {
        if (!kingdeeCloudService.isEnabled()) {
            return PageResult.of(mockBills(billType), mockBills(billType).size(), current, size);
        }
        List<KingdeeReceiveBillVo> all = billType.isUseInspectionFilter()
                ? pageWithInspection(billType, keyword)
                : pageApproved(billType, keyword);
        int from = (int) Math.max(0, (current - 1) * size);
        int to = (int) Math.min(all.size(), from + size);
        List<KingdeeReceiveBillVo> slice = from < all.size() ? all.subList(from, to) : List.of();
        return PageResult.of(slice, all.size(), current, size);
    }

    public KingdeeReceiveBillVo getBill(NoticeBillType billType, String billNo) {
        if (!StringUtils.hasText(billNo)) {
            return null;
        }
        String no = billNo.trim();
        if (kingdeeCloudService.isEnabled()) {
            // 列表 FieldKeys 查询更轻；View 作补全
            KingdeeReceiveBillVo bill = pullFromQuery(billType, no);
            if (bill != null && bill.getLines() != null && !bill.getLines().isEmpty()) {
                return bill;
            }
            KingdeeReceiveBillVo viewBill = pullFromView(billType, no);
            if (viewBill != null) {
                return viewBill;
            }
            if (bill != null) {
                return bill;
            }
        }
        return mockBills(billType).stream()
                .filter(b -> no.equalsIgnoreCase(b.getBillNo())
                        || (billType == NoticeBillType.PRODUCTION_ISSUE
                        && ("SCL20260715001".equalsIgnoreCase(no) || "YLD20260715001".equalsIgnoreCase(no))
                        && "YLD20260715001".equalsIgnoreCase(b.getBillNo()))
                        || (billType == NoticeBillType.OUTSOURCE_ISSUE
                        && ("WWC20260715001".equalsIgnoreCase(no) || "YWW20260715001".equalsIgnoreCase(no))
                        && "YWW20260715001".equalsIgnoreCase(b.getBillNo()))
                        || (billType == NoticeBillType.PRODUCTION_RETURN
                        && ("SCT20260715001".equalsIgnoreCase(no) || "SCL20260715001".equalsIgnoreCase(no))
                        && "SCL20260715001".equalsIgnoreCase(b.getBillNo()))
                        || (billType == NoticeBillType.OUTSOURCE_RETURN
                        && ("WWT20260715001".equalsIgnoreCase(no) || "WWL20260715001".equalsIgnoreCase(no))
                        && "WWL20260715001".equalsIgnoreCase(b.getBillNo())))
                .findFirst()
                .orElse(null);
    }

    public String parseBillNo(String barcode) {
        if (!StringUtils.hasText(barcode)) {
            return "";
        }
        String raw = barcode.trim();
        if (raw.matches("(?i)[A-Z]{2,6}\\d{6,}")) {
            return raw.toUpperCase();
        }
        var matcher = java.util.regex.Pattern.compile("(?i)([A-Z]{2,6}\\d{6,})").matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return raw;
    }

    private List<KingdeeReceiveBillVo> pageWithInspection(NoticeBillType billType, String keyword) {
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                resolveFormId(billType),
                properties.getReceiveBillInspectionListFieldKeys(),
                buildFilter(billType, keyword),
                "FDate desc, FBillNo desc",
                0,
                resolveListQueryLimit());
        List<KingdeeReceiveBillInspectionLine> inspectionRows = new ArrayList<>();
        for (List<String> row : rows) {
            KingdeeReceiveBillInspectionLine line = mapInspectionRow(row);
            if (line != null) {
                inspectionRows.add(line);
            }
        }
        if (inspectionRows.isEmpty()) {
            return pageApproved(billType, keyword);
        }
        Map<String, Integer> lineCounts = countLinesPerBill(inspectionRows);
        List<KingdeeReceiveBillVo> eligible = KingdeeReceiveBillInspectionFilter.filterEligibleBills(inspectionRows).stream()
                .map(line -> toListBill(line, resolveMaterialLineCount(line, lineCounts)))
                .collect(Collectors.toList());
        if (!eligible.isEmpty()) {
            return eligible;
        }
        log.warn("Kingdee inspection filter removed all lines for billType={}, fallback approved list", billType.getCode());
        return pageApproved(billType, keyword);
    }

    private List<KingdeeReceiveBillVo> pageApproved(NoticeBillType billType, String keyword) {
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                resolveFormId(billType),
                resolveListFieldKeys(billType),
                buildFilter(billType, keyword),
                "FBillNo",
                0,
                resolveListQueryLimit());
        Map<String, KingdeeReceiveBillVo> bills = new LinkedHashMap<>();
        Map<String, Integer> lineCounts = new LinkedHashMap<>();
        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String billNo = cell(row, 0);
            if (!StringUtils.hasText(billNo)) {
                continue;
            }
            String no = billNo.trim();
            lineCounts.merge(no, 1, Integer::sum);
            bills.putIfAbsent(no, KingdeeReceiveBillVo.builder()
                    .billNo(no)
                    .supplierCode(cell(row, 1))
                    .supplierName(cell(row, 2))
                    .documentStatus(cell(row, 3))
                    .billDate(parseDate(cell(row, 4)))
                    .warehouseCode("WH01")
                    .totalLines(0)
                    .build());
        }
        for (Map.Entry<String, KingdeeReceiveBillVo> entry : bills.entrySet()) {
            entry.getValue().setTotalLines(lineCounts.getOrDefault(entry.getKey(), 0));
        }
        return new ArrayList<>(bills.values());
    }

    private String resolveListFieldKeys(NoticeBillType billType) {
        if (billType == NoticeBillType.PRODUCTION_ISSUE) {
            return properties.getPpBomLineCountFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            return properties.getSubPpBomLineCountFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return properties.getPickMtrlLineCountFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return properties.getSubPickMtrlLineCountFieldKeys();
        }
        return properties.getReceiveBillLineCountFieldKeys();
    }

    private String resolveDetailFieldKeys(NoticeBillType billType) {
        if (billType == NoticeBillType.PRODUCTION_ISSUE) {
            return properties.getPpBomDetailFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            return properties.getSubPpBomDetailFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return properties.getPickMtrlDetailFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return properties.getSubPickMtrlDetailFieldKeys();
        }
        return properties.getReceiveBillDetailFieldKeys();
    }

    private KingdeeReceiveBillVo pullFromView(NoticeBillType billType, String billNo) {
        try {
            JsonNode billNode = kingdeeCloudService.viewBill(resolveFormId(billType), billNo);
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
        } catch (Exception e) {
            log.warn("Kingdee View failed billType={} billNo={}", billType.getCode(), billNo, e);
            return null;
        }
    }

    private KingdeeReceiveBillVo pullFromQuery(NoticeBillType billType, String billNo) {
        String filter = buildFilter(billType, null) + " and FBillNo='" + escapeFilter(billNo) + "'";
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                resolveFormId(billType),
                resolveDetailFieldKeys(billType),
                filter,
                "FBillNo",
                0,
                properties.getReceiveBillQueryLimit());
        if (billType == NoticeBillType.PRODUCTION_ISSUE) {
            return buildPpBomFromDetailRows(rows, billNo);
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            return buildSubPpBomFromDetailRows(rows, billNo);
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return buildPickMtrlFromDetailRows(rows, billNo);
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return buildSubPickMtrlFromDetailRows(rows, billNo);
        }
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

    /**
     * 用料清单 Query 回退：FieldKeys 见 ppBomDetailFieldKeys。
     * 索引约定：0单号 1FID 2日期 3车间码 4车间名 5生产订单
     * 头产品 6/7；分录物料 8/9；数量 10应发 11已领 12未领；13行号 14分录内码 15批号
     * 16单位 17仓库 18MO单号 19MoId 20MoEntryId 21MoEntrySeq 22父项物料
     */
    private KingdeeReceiveBillVo buildPpBomFromDetailRows(List<List<String>> rows, String billNo) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        KingdeeReceiveBillVo bill = null;
        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String no = cell(row, 0);
            if (!StringUtils.hasText(no)) {
                continue;
            }
            if (bill == null) {
                bill = KingdeeReceiveBillVo.builder()
                        .billNo(no.trim())
                        .billId(parseLong(cell(row, 1)))
                        .billDate(parseDate(cell(row, 2)))
                        .supplierCode(cell(row, 3))
                        .supplierName(cell(row, 4))
                        .moBillNo(firstNonBlank(cell(row, 5), cell(row, 18)))
                        .parentMaterialCode(firstNonBlank(cell(row, 6), cell(row, 22)))
                        .warehouseCode(firstNonBlank(cell(row, 17), "CK004"))
                        .documentStatus("C")
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = firstNonBlank(cell(row, 8), cell(row, 6));
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal mustQty = parseDecimal(cell(row, 10));
            BigDecimal pickedQty = parseDecimal(cell(row, 11));
            BigDecimal noPicked = parseDecimal(cell(row, 12));
            BigDecimal planQty = noPicked.compareTo(BigDecimal.ZERO) > 0
                    ? noPicked
                    : mustQty.subtract(pickedQty).max(BigDecimal.ZERO);
            if (planQty.compareTo(BigDecimal.ZERO) <= 0 && mustQty.compareTo(BigDecimal.ZERO) > 0) {
                planQty = mustQty;
            }
            int seq = parseInt(cell(row, 13));
            if (seq <= 0) {
                seq = bill.getLines().size() + 1;
            }
            bill.getLines().add(KingdeeReceiveBillLineVo.builder()
                    .lineNo(seq)
                    .materialCode(materialCode.trim())
                    .materialName(firstNonBlank(cell(row, 9), cell(row, 7)))
                    .batchNo(cell(row, 15))
                    .unitCode(cell(row, 16))
                    .stockWarehouseCode(cell(row, 17))
                    .entryId(parseLong(cell(row, 14)))
                    .ppBomEntryId(parseLong(cell(row, 14)))
                    .ppBomBillNo(no.trim())
                    .planQty(planQty)
                    .moBillNo(firstNonBlank(cell(row, 18), bill.getMoBillNo()))
                    .moId(parseLong(cell(row, 19)))
                    .moEntryId(parseLong(cell(row, 20)))
                    .moEntrySeq(parseInt(cell(row, 21)) > 0 ? parseInt(cell(row, 21)) : null)
                    .parentMaterialCode(firstNonBlank(cell(row, 22), bill.getParentMaterialCode()))
                    .receivedQty(BigDecimal.ZERO)
                    .build());
        }
        if (bill != null) {
            bill.setTotalLines(bill.getLines().size());
        }
        return bill != null && billNo.equalsIgnoreCase(bill.getBillNo()) ? bill : bill;
    }

    /**
     * 委外用料清单 Query 回退：FieldKeys 见 subPpBomDetailFieldKeys。
     * 索引约定：0单号 1FID 2日期 3供应商码 4供应商名 5委外订单
     * 头产品 6/7；分录物料 8/9；数量 10应发 11已领 12未领；13行号 14分录内码 15批号
     * 16单位 17仓库 18SubReq单号 19SubReqId 20SubReqEntryId 21SubReqEntrySeq 22父项物料
     */
    private KingdeeReceiveBillVo buildSubPpBomFromDetailRows(List<List<String>> rows, String billNo) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        KingdeeReceiveBillVo bill = null;
        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String no = cell(row, 0);
            if (!StringUtils.hasText(no)) {
                continue;
            }
            if (bill == null) {
                bill = KingdeeReceiveBillVo.builder()
                        .billNo(no.trim())
                        .billId(parseLong(cell(row, 1)))
                        .billDate(parseDate(cell(row, 2)))
                        .supplierCode(cell(row, 3))
                        .supplierName(cell(row, 4))
                        .moBillNo(firstNonBlank(cell(row, 5), cell(row, 18)))
                        .parentMaterialCode(firstNonBlank(cell(row, 6), cell(row, 22)))
                        .warehouseCode(firstNonBlank(cell(row, 17), "CK004"))
                        .documentStatus("C")
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = firstNonBlank(cell(row, 8), cell(row, 6));
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal mustQty = parseDecimal(cell(row, 10));
            BigDecimal pickedQty = parseDecimal(cell(row, 11));
            BigDecimal noPicked = parseDecimal(cell(row, 12));
            BigDecimal planQty = noPicked.compareTo(BigDecimal.ZERO) > 0
                    ? noPicked
                    : mustQty.subtract(pickedQty).max(BigDecimal.ZERO);
            if (planQty.compareTo(BigDecimal.ZERO) <= 0 && mustQty.compareTo(BigDecimal.ZERO) > 0) {
                planQty = mustQty;
            }
            int seq = parseInt(cell(row, 13));
            if (seq <= 0) {
                seq = bill.getLines().size() + 1;
            }
            Long entryId = parseLong(cell(row, 14));
            bill.getLines().add(KingdeeReceiveBillLineVo.builder()
                    .lineNo(seq)
                    .materialCode(materialCode.trim())
                    .materialName(firstNonBlank(cell(row, 9), cell(row, 7)))
                    .batchNo(cell(row, 15))
                    .unitCode(cell(row, 16))
                    .stockWarehouseCode(cell(row, 17))
                    .entryId(entryId)
                    .ppBomEntryId(entryId)
                    .ppBomBillNo(no.trim())
                    .planQty(planQty)
                    .subReqBillNo(firstNonBlank(cell(row, 18), bill.getMoBillNo()))
                    .subReqId(parseLong(cell(row, 19)))
                    .subReqEntryId(parseLong(cell(row, 20)))
                    .subReqEntrySeq(parseInt(cell(row, 21)) > 0 ? parseInt(cell(row, 21)) : null)
                    .parentMaterialCode(firstNonBlank(cell(row, 22), bill.getParentMaterialCode()))
                    .receivedQty(BigDecimal.ZERO)
                    .build());
        }
        if (bill != null) {
            bill.setTotalLines(bill.getLines().size());
        }
        return bill != null && billNo.equalsIgnoreCase(bill.getBillNo()) ? bill : bill;
    }

    /**
     * 生产领料单 Query 回退（生产退料源单）：FieldKeys 见 pickMtrlDetailFieldKeys。
     * 索引约定与用料清单类似，数量 10=实发 11=申请 12=基本实发。
     */
    private KingdeeReceiveBillVo buildPickMtrlFromDetailRows(List<List<String>> rows, String billNo) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        KingdeeReceiveBillVo bill = null;
        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String no = cell(row, 0);
            if (!StringUtils.hasText(no)) {
                continue;
            }
            if (bill == null) {
                bill = KingdeeReceiveBillVo.builder()
                        .billNo(no.trim())
                        .billId(parseLong(cell(row, 1)))
                        .billDate(parseDate(cell(row, 2)))
                        .supplierCode(cell(row, 3))
                        .supplierName(cell(row, 4))
                        .moBillNo(firstNonBlank(cell(row, 5), cell(row, 18)))
                        .parentMaterialCode(firstNonBlank(cell(row, 6), cell(row, 22)))
                        .warehouseCode(firstNonBlank(cell(row, 17), "CK004"))
                        .documentStatus("C")
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = firstNonBlank(cell(row, 8), cell(row, 6));
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal actualQty = parseDecimal(cell(row, 10));
            BigDecimal appQty = parseDecimal(cell(row, 11));
            BigDecimal planQty = actualQty.compareTo(BigDecimal.ZERO) > 0 ? actualQty : appQty;
            int seq = parseInt(cell(row, 13));
            if (seq <= 0) {
                seq = bill.getLines().size() + 1;
            }
            Long entryId = parseLong(cell(row, 14));
            bill.getLines().add(KingdeeReceiveBillLineVo.builder()
                    .lineNo(seq)
                    .materialCode(materialCode.trim())
                    .materialName(firstNonBlank(cell(row, 9), cell(row, 7)))
                    .batchNo(cell(row, 15))
                    .unitCode(cell(row, 16))
                    .stockWarehouseCode(cell(row, 17))
                    .entryId(entryId)
                    .ppBomEntryId(parseLong(cell(row, 23)))
                    .ppBomBillNo(cell(row, 24))
                    .planQty(planQty)
                    .moBillNo(firstNonBlank(cell(row, 18), bill.getMoBillNo()))
                    .moId(parseLong(cell(row, 19)))
                    .moEntryId(parseLong(cell(row, 20)))
                    .moEntrySeq(parseInt(cell(row, 21)) > 0 ? parseInt(cell(row, 21)) : null)
                    .parentMaterialCode(firstNonBlank(cell(row, 22), bill.getParentMaterialCode()))
                    .receivedQty(BigDecimal.ZERO)
                    .build());
        }
        if (bill != null) {
            bill.setTotalLines(bill.getLines().size());
        }
        return bill != null && billNo.equalsIgnoreCase(bill.getBillNo()) ? bill : bill;
    }

    /**
     * 委外领料单 Query 回退（委外退料源单）：FieldKeys 见 subPickMtrlDetailFieldKeys。
     * 索引约定与生产领料单一致，数量 10=实发 11=申请；18-21 为委外订单字段。
     */
    private KingdeeReceiveBillVo buildSubPickMtrlFromDetailRows(List<List<String>> rows, String billNo) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        KingdeeReceiveBillVo bill = null;
        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String no = cell(row, 0);
            if (!StringUtils.hasText(no)) {
                continue;
            }
            if (bill == null) {
                bill = KingdeeReceiveBillVo.builder()
                        .billNo(no.trim())
                        .billId(parseLong(cell(row, 1)))
                        .billDate(parseDate(cell(row, 2)))
                        .supplierCode(cell(row, 3))
                        .supplierName(cell(row, 4))
                        .moBillNo(firstNonBlank(cell(row, 5), cell(row, 18)))
                        .parentMaterialCode(firstNonBlank(cell(row, 6), cell(row, 22)))
                        .warehouseCode(firstNonBlank(cell(row, 17), "CK004"))
                        .documentStatus("C")
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = firstNonBlank(cell(row, 8), cell(row, 6));
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal actualQty = parseDecimal(cell(row, 10));
            BigDecimal appQty = parseDecimal(cell(row, 11));
            BigDecimal planQty = actualQty.compareTo(BigDecimal.ZERO) > 0 ? actualQty : appQty;
            int seq = parseInt(cell(row, 13));
            if (seq <= 0) {
                seq = bill.getLines().size() + 1;
            }
            Long entryId = parseLong(cell(row, 14));
            bill.getLines().add(KingdeeReceiveBillLineVo.builder()
                    .lineNo(seq)
                    .materialCode(materialCode.trim())
                    .materialName(firstNonBlank(cell(row, 9), cell(row, 7)))
                    .batchNo(cell(row, 15))
                    .unitCode(cell(row, 16))
                    .stockWarehouseCode(cell(row, 17))
                    .entryId(entryId)
                    .ppBomEntryId(parseLong(cell(row, 23)))
                    .ppBomBillNo(cell(row, 24))
                    .planQty(planQty)
                    .subReqBillNo(firstNonBlank(cell(row, 18), bill.getMoBillNo()))
                    .subReqId(parseLong(cell(row, 19)))
                    .subReqEntryId(parseLong(cell(row, 20)))
                    .subReqEntrySeq(parseInt(cell(row, 21)) > 0 ? parseInt(cell(row, 21)) : null)
                    .parentMaterialCode(firstNonBlank(cell(row, 22), bill.getParentMaterialCode()))
                    .receivedQty(BigDecimal.ZERO)
                    .build());
        }
        if (bill != null) {
            bill.setTotalLines(bill.getLines().size());
        }
        return bill != null && billNo.equalsIgnoreCase(bill.getBillNo()) ? bill : bill;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
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
                    .lines(new ArrayList<>())
                    .build());
            if (bill.getBillId() == null) {
                bill.setBillId(KingdeeReceiveBillDetailRowParser.billId(row));
            }
            bill.getLines().add(KingdeeReceiveBillDetailRowParser.mapLine(row, bill.getLines().size() + 1));
        }
        for (KingdeeReceiveBillVo bill : bills.values()) {
            bill.setTotalLines(bill.getLines().size());
        }
        return bills;
    }

    private KingdeeReceiveBillLineVo mapDetailLine(List<String> row, int fallbackSeq) {
        return KingdeeReceiveBillDetailRowParser.mapLine(row, fallbackSeq);
    }

    private KingdeeReceiveBillInspectionLine mapInspectionRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        String billNo = cell(row, 0);
        if (!StringUtils.hasText(billNo)) {
            return null;
        }
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
                .build();
    }

    private KingdeeReceiveBillVo toListBill(KingdeeReceiveBillInspectionLine line, int materialLineCount) {
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

    private Map<String, Integer> countLinesPerBill(List<KingdeeReceiveBillInspectionLine> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (KingdeeReceiveBillInspectionLine row : rows) {
            if (row == null || !StringUtils.hasText(row.getBillNo())) {
                continue;
            }
            counts.merge(row.getBillNo().trim(), 1, Integer::sum);
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

    private String buildFilter(NoticeBillType billType, String keyword) {
        String filter = "FDocumentStatus='C'";
        if (StringUtils.hasText(billType.getExtraFilter())) {
            filter += " and " + billType.getExtraFilter();
        }
        if (StringUtils.hasText(keyword)) {
            String kw = escapeFilter(keyword);
            if (billType == NoticeBillType.PRODUCTION_ISSUE) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FWorkShopId.FName like '%" + kw + "%'"
                        + " or FWorkShopId.FNumber like '%" + kw + "%'"
                        + " or FMOBillNO like '%" + kw + "%')";
            } else if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FSupplierId.FName like '%" + kw + "%'"
                        + " or FSupplierId.FNumber like '%" + kw + "%'"
                        + " or FSubReqBillNo like '%" + kw + "%')";
            } else if (billType == NoticeBillType.PRODUCTION_RETURN) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FWorkShopId.FName like '%" + kw + "%'"
                        + " or FWorkShopId.FNumber like '%" + kw + "%')";
            } else if (billType == NoticeBillType.OUTSOURCE_RETURN) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FSupplierId.FName like '%" + kw + "%'"
                        + " or FSupplierId.FNumber like '%" + kw + "%'"
                        + " or FEntity_FSubReqBillNo like '%" + kw + "%')";
            } else {
                filter += " and (FBillNo like '%" + kw + "%' or FSupplierId.FName like '%" + kw + "%')";
            }
        }
        return filter;
    }

    private String resolveFormId(NoticeBillType billType) {
        if (billType == NoticeBillType.PURCHASE_RECEIVE) {
            return properties.getReceiveBillFormId();
        }
        return billType.getDefaultFormId();
    }

    private int resolveListQueryLimit() {
        int configured = properties.getReceiveBillListQueryLimit();
        int pdaLimit = pdaProperties.getListFetchLimit();
        if (pdaLimit <= 0) {
            return configured;
        }
        return Math.min(configured, Math.max(pdaLimit * 3, pdaLimit));
    }

    private List<KingdeeReceiveBillVo> mockBills(NoticeBillType billType) {
        if (billType == NoticeBillType.PRODUCTION_ISSUE) {
            return List.of(productionIssueMockBill());
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            return List.of(outsourceIssueMockBill());
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return List.of(productionReturnMockBill());
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return List.of(outsourceReturnMockBill());
        }
        String prefix = switch (billType) {
            case PRODUCTION_IN -> "SCR";
            case PRODUCTION_RETURN -> "SCT";
            case OUTSOURCE_RETURN -> "WWT";
            case OTHER_IN -> "QTR";
            case SALES_DELIVERY -> "XSF";
            case PRODUCTION_ISSUE -> "SCL";
            case OUTSOURCE_ISSUE -> "WWC";
            case OTHER_OUT -> "QTC";
            default -> "SLD";
        };
        return List.of(
                KingdeeReceiveBillVo.builder()
                        .billNo(prefix + "20260708001")
                        .billDate(LocalDate.now())
                        .supplierCode("SUP001")
                        .supplierName(billType.getLabel() + "-模拟供应商")
                        .documentStatus("C")
                        .warehouseCode("CK004")
                        .totalLines(2)
                        .lines(List.of(
                                line(1, "MAT-10001", "模拟物料A", "PCS", "100"),
                                line(2, "MAT-10002", "模拟物料B", "PCS", "50")))
                        .build());
    }

    /** 生产退料源单联调：扫生产领料单 SCL20260715001 */
    private KingdeeReceiveBillVo productionReturnMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("SCL20260715001")
                .billId(90016001L)
                .billDate(LocalDate.now())
                .supplierCode("WS01")
                .supplierName("一车间")
                .moBillNo("MO20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("C")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        pickLine(1, 90016011L, "PITEST-001", "螺栓 M8", "M8×20", "B20260715", "PCS", "100", "CK004"),
                        pickLine(2, 90016012L, "PITEST-002", "垫片 Φ8", "Φ8×1.5", "B20260715", "PCS", "50", "CK004")))
                .build();
    }

    /** 委外退料源单联调：扫委外领料单 WWL20260715001 */
    private KingdeeReceiveBillVo outsourceReturnMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("WWL20260715001")
                .billId(90018001L)
                .billDate(LocalDate.now())
                .supplierCode("SUP001")
                .supplierName("模拟委外供应商")
                .moBillNo("WWDD20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("C")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        subPickLine(1, 90018011L, "PITEST-001", "螺栓 M8", "M8×20", "B20260715", "PCS", "100", "CK004"),
                        subPickLine(2, 90018012L, "PITEST-002", "垫片 Φ8", "Φ8×1.5", "B20260715", "PCS", "50", "CK004")))
                .build();
    }

    private KingdeeReceiveBillLineVo pickLine(int no, long entryId, String code, String name, String spec,
                                              String batchNo, String unit, String qty, String stockCode) {
        return KingdeeReceiveBillLineVo.builder()
                .lineNo(no)
                .materialCode(code)
                .materialName(name)
                .specification(spec)
                .batchNo(batchNo)
                .planQty(new BigDecimal(qty))
                .unitCode(unit)
                .stockWarehouseCode(stockCode)
                .entryId(entryId)
                .ppBomEntryId(entryId)
                .ppBomBillNo("YLD20260715001")
                .moBillNo("MO20260715001")
                .moId(80015001L)
                .moEntryId(80015011L)
                .moEntrySeq(1)
                .parentMaterialCode("FG-TEST-001")
                .receivedQty(BigDecimal.ZERO)
                .build();
    }

    private KingdeeReceiveBillLineVo subPickLine(int no, long entryId, String code, String name, String spec,
                                                 String batchNo, String unit, String qty, String stockCode) {
        return KingdeeReceiveBillLineVo.builder()
                .lineNo(no)
                .materialCode(code)
                .materialName(name)
                .specification(spec)
                .batchNo(batchNo)
                .planQty(new BigDecimal(qty))
                .unitCode(unit)
                .stockWarehouseCode(stockCode)
                .entryId(entryId)
                .ppBomEntryId(entryId)
                .ppBomBillNo("YWW20260715001")
                .subReqBillNo("WWDD20260715001")
                .subReqId(80018001L)
                .subReqEntryId(80018011L)
                .subReqEntrySeq(1)
                .parentMaterialCode("FG-TEST-001")
                .receivedQty(BigDecimal.ZERO)
                .build();
    }

    /** 生产用料清单联调数据：单号 YLD20260715001，扫码示例见物料批号条码 */
    private KingdeeReceiveBillVo productionIssueMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("YLD20260715001")
                .billId(90015001L)
                .billDate(LocalDate.now())
                .supplierCode("WS01")
                .supplierName("一车间")
                .moBillNo("MO20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("C")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        ppBomLine(1, 90015011L, "PITEST-001", "螺栓 M8", "M8×20", "B20260715", "PCS", "100", "CK004"),
                        ppBomLine(2, 90015012L, "PITEST-002", "垫片 Φ8", "Φ8×1.5", "B20260715", "PCS", "50", "CK004")))
                .build();
    }

    /** 委外用料清单联调：单号 YWW20260715001 */
    private KingdeeReceiveBillVo outsourceIssueMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("YWW20260715001")
                .billId(90017001L)
                .billDate(LocalDate.now())
                .supplierCode("SUP001")
                .supplierName("模拟委外供应商")
                .moBillNo("WWDD20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("C")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        subPpBomLine(1, 90017011L, "PITEST-001", "螺栓 M8", "M8×20", "B20260715", "PCS", "100", "CK004"),
                        subPpBomLine(2, 90017012L, "PITEST-002", "垫片 Φ8", "Φ8×1.5", "B20260715", "PCS", "50", "CK004")))
                .build();
    }

    private KingdeeReceiveBillLineVo ppBomLine(int no, long entryId, String code, String name, String spec,
                                               String batchNo, String unit, String qty, String stockCode) {
        return KingdeeReceiveBillLineVo.builder()
                .lineNo(no)
                .materialCode(code)
                .materialName(name)
                .specification(spec)
                .batchNo(batchNo)
                .planQty(new BigDecimal(qty))
                .unitCode(unit)
                .stockWarehouseCode(stockCode)
                .entryId(entryId)
                .ppBomEntryId(entryId)
                .ppBomBillNo("YLD20260715001")
                .moBillNo("MO20260715001")
                .moId(80015001L)
                .moEntryId(80015011L)
                .moEntrySeq(1)
                .parentMaterialCode("FG-TEST-001")
                .receivedQty(BigDecimal.ZERO)
                .build();
    }

    private KingdeeReceiveBillLineVo subPpBomLine(int no, long entryId, String code, String name, String spec,
                                                  String batchNo, String unit, String qty, String stockCode) {
        return KingdeeReceiveBillLineVo.builder()
                .lineNo(no)
                .materialCode(code)
                .materialName(name)
                .specification(spec)
                .batchNo(batchNo)
                .planQty(new BigDecimal(qty))
                .unitCode(unit)
                .stockWarehouseCode(stockCode)
                .entryId(entryId)
                .ppBomEntryId(entryId)
                .ppBomBillNo("YWW20260715001")
                .subReqBillNo("WWDD20260715001")
                .subReqId(80017001L)
                .subReqEntryId(80017011L)
                .subReqEntrySeq(1)
                .parentMaterialCode("FG-TEST-001")
                .receivedQty(BigDecimal.ZERO)
                .build();
    }

    private KingdeeReceiveBillLineVo line(int no, String code, String name, String unit, String qty) {
        return line(no, code, name, null, null, unit, qty, null);
    }

    private KingdeeReceiveBillLineVo line(int no, String code, String name, String spec,
                                          String batchNo, String unit, String qty, String stockCode) {
        return KingdeeReceiveBillLineVo.builder()
                .lineNo(no)
                .materialCode(code)
                .materialName(name)
                .specification(spec)
                .batchNo(batchNo)
                .planQty(new BigDecimal(qty))
                .unitCode(unit)
                .stockWarehouseCode(stockCode)
                .receivedQty(BigDecimal.ZERO)
                .build();
    }

    private String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
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
