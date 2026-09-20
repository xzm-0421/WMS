package com.wms.noticebill.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.common.exception.BusinessException;
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
            if (!kingdeeCloudService.isMockEnabled()) {
                return PageResult.of(List.of(), 0, current, size);
            }
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
            // 列表 FieldKeys 查询更轻；数量全 0 时强制 View 补全（补料/领料常见 Query 字段空）
            KingdeeReceiveBillVo bill = pullFromQuery(billType, no);
            if (bill != null && bill.getLines() != null && !bill.getLines().isEmpty()
                    && isAllowedDocumentStatus(billType, bill)
                    && !isAllPlanQtyZero(bill)) {
                return bill;
            }
            KingdeeReceiveBillVo viewBill = pullFromView(billType, no);
            if (viewBill != null && isAllowedDocumentStatus(billType, viewBill)
                    && viewBill.getLines() != null && !viewBill.getLines().isEmpty()) {
                return mergeQueryMetaIntoView(bill, viewBill);
            }
            if (bill != null && isAllowedDocumentStatus(billType, bill)) {
                return bill;
            }
        }
        if (!kingdeeCloudService.isMockEnabled()) {
            return null;
        }
        return mockBills(billType).stream()
                .filter(b -> no.equalsIgnoreCase(b.getBillNo())
                        || (billType == NoticeBillType.PRODUCTION_ISSUE
                        && ("SCL20260715001".equalsIgnoreCase(no) || "SOUT20260715001".equalsIgnoreCase(no))
                        && "SCL20260715001".equalsIgnoreCase(b.getBillNo()))
                        || (billType == NoticeBillType.PRODUCTION_FEED
                        && ("SCB20260715001".equalsIgnoreCase(no) || "SCL20260715001".equalsIgnoreCase(no))
                        && "SCB20260715001".equalsIgnoreCase(b.getBillNo()))
                        || (billType == NoticeBillType.OUTSOURCE_ISSUE
                        && ("WWL20260715001".equalsIgnoreCase(no) || "YWW20260715001".equalsIgnoreCase(no))
                        && "WWL20260715001".equalsIgnoreCase(b.getBillNo()))
                        || (billType == NoticeBillType.OUTSOURCE_FEED
                        && ("WWB20260715001".equalsIgnoreCase(no) || "WWL20260715001".equalsIgnoreCase(no))
                        && "WWB20260715001".equalsIgnoreCase(b.getBillNo()))
                        || (billType == NoticeBillType.PRODUCTION_RETURN
                        && ("SCT20260715001".equalsIgnoreCase(no) || "SCL20260715001".equalsIgnoreCase(no))
                        && "SCT20260715001".equalsIgnoreCase(b.getBillNo()))
                        || (billType == NoticeBillType.OUTSOURCE_RETURN
                        && ("WWT20260715001".equalsIgnoreCase(no) || "WWL20260715001".equalsIgnoreCase(no))
                        && "WWT20260715001".equalsIgnoreCase(b.getBillNo())))
                .findFirst()
                .orElse(null);
    }

    private static boolean isAllPlanQtyZero(KingdeeReceiveBillVo bill) {
        if (bill == null || bill.getLines() == null || bill.getLines().isEmpty()) {
            return true;
        }
        return bill.getLines().stream().allMatch(line ->
                line == null
                        || line.getPlanQty() == null
                        || line.getPlanQty().compareTo(BigDecimal.ZERO) <= 0);
    }

    /**
     * View 有数量时优先用 View；Query 上的分录内码/仓库等仍尽量保留。
     */
    private static KingdeeReceiveBillVo mergeQueryMetaIntoView(KingdeeReceiveBillVo queryBill,
                                                               KingdeeReceiveBillVo viewBill) {
        if (viewBill == null) {
            return queryBill;
        }
        if (queryBill == null) {
            return viewBill;
        }
        if (viewBill.getBillId() == null || viewBill.getBillId() <= 0) {
            viewBill.setBillId(queryBill.getBillId());
        }
        if (!StringUtils.hasText(viewBill.getCreatorKdUserNumber())
                && StringUtils.hasText(queryBill.getCreatorKdUserNumber())) {
            viewBill.setCreatorKdUserNumber(queryBill.getCreatorKdUserNumber());
        }
        if (!StringUtils.hasText(viewBill.getCreatorName())
                && StringUtils.hasText(queryBill.getCreatorName())) {
            viewBill.setCreatorName(queryBill.getCreatorName());
        }
        if (viewBill.getLines() == null || queryBill.getLines() == null) {
            return viewBill;
        }
        for (KingdeeReceiveBillLineVo viewLine : viewBill.getLines()) {
            if (viewLine == null) {
                continue;
            }
            KingdeeReceiveBillLineVo q = findMatchingLine(queryBill.getLines(), viewLine);
            if (q == null) {
                continue;
            }
            if ((viewLine.getEntryId() == null || viewLine.getEntryId() <= 0)
                    && q.getEntryId() != null && q.getEntryId() > 0) {
                viewLine.setEntryId(q.getEntryId());
            }
            if (!StringUtils.hasText(viewLine.getStockWarehouseCode())
                    && StringUtils.hasText(q.getStockWarehouseCode())) {
                viewLine.setStockWarehouseCode(q.getStockWarehouseCode());
            }
        }
        return viewBill;
    }

    private static KingdeeReceiveBillLineVo findMatchingLine(List<KingdeeReceiveBillLineVo> lines,
                                                             KingdeeReceiveBillLineVo target) {
        if (lines == null || target == null) {
            return null;
        }
        if (target.getLineNo() != null) {
            for (KingdeeReceiveBillLineVo line : lines) {
                if (line != null && target.getLineNo().equals(line.getLineNo())) {
                    return line;
                }
            }
        }
        if (StringUtils.hasText(target.getMaterialCode())) {
            String code = target.getMaterialCode().trim();
            for (KingdeeReceiveBillLineVo line : lines) {
                if (line != null && code.equalsIgnoreCase(
                        line.getMaterialCode() != null ? line.getMaterialCode().trim() : "")) {
                    return line;
                }
            }
        }
        return null;
    }

    /** 未审核工作流单据允许 Z/A/B/D；已审源单下推仅允许 C；其它类型不限制（列表已按状态过滤） */
    private boolean isAllowedDocumentStatus(NoticeBillType billType, KingdeeReceiveBillVo bill) {
        if (billType == null || bill == null) {
            return true;
        }
        String status = bill.getDocumentStatus();
        if (!StringUtils.hasText(status)) {
            // Query 明细 FieldKeys 不含状态时，依赖 FilterString 已限制
            return true;
        }
        String s = status.trim().toUpperCase();
        if (billType.isAuditedSourcePushBill()) {
            return "C".equals(s);
        }
        if (billType.isSubmittedInProcessListBill() || billType.isUnauditedWorkflowBill()) {
            return isUnauditedDocumentStatus(s);
        }
        return true;
    }

    /**
     * 从单据二维码/条码中提取单号。支持：纯单号、JSON（billNo/FBillNo 等）、URL、前缀、混杂文本中的单号。
     */
    public String parseBillNo(String barcode) {
        return KingdeeBillNoParser.parse(barcode);
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
        String formId = resolveFormId(billType);
        String filter = buildListFilter(billType, keyword);
        String fieldKeys = resolveListFieldKeys(billType);
        List<List<String>> rows = queryApprovedListRows(billType, formId, fieldKeys, filter, keyword);
        log.info("Kingdee list billType={} formId={} filter={} rows={}",
                billType.getCode(), formId, filter, rows.size());
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
            if (billType.isOpenRemainOutQtyListBill() && !isNonZeroRemainOutQty(cell(row, 6))) {
                continue;
            }
            lineCounts.merge(no, 1, Integer::sum);
            bills.putIfAbsent(no, KingdeeReceiveBillVo.builder()
                    .billNo(no)
                    .supplierCode(cell(row, 1))
                    .supplierName(cell(row, 2))
                    .documentStatus(cell(row, 3))
                    .billDate(parseDate(cell(row, 4)))
                    .warehouseCode("WH01")
                    .creatorKdUserNumber(billType.isOpenRemainOutQtyListBill() ? null : cell(row, 6))
                    .creatorName(billType.isOpenRemainOutQtyListBill() ? null : cell(row, 7))
                    .totalLines(0)
                    .build());
        }
        for (Map.Entry<String, KingdeeReceiveBillVo> entry : bills.entrySet()) {
            entry.getValue().setTotalLines(lineCounts.getOrDefault(entry.getKey(), 0));
        }
        return new ArrayList<>(bills.values());
    }

    /** 生产领料列表表头字段：不依赖分录，避免无分录单在 ExecuteBillQuery 中消失 */
    private String resolvePickMtrlHeaderListFieldKeys() {
        return "FBillNo,FWorkShopId.FNumber,FWorkShopId.FName,FDocumentStatus,FDate";
    }

    private String resolveListFieldKeys(NoticeBillType billType) {
        if (billType == NoticeBillType.PRODUCTION_ISSUE) {
            return resolvePickMtrlHeaderListFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_FEED) {
            return properties.getFeedMtrlLineCountFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return properties.getReturnMtrlLineCountFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_IN) {
            return properties.getPrdMorptLineCountFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_RET_STOCK) {
            return properties.getPrdRetStockLineCountFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            return properties.getSubPickMtrlLineCountFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_FEED) {
            return properties.getSubFeedMtrlLineCountFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return properties.getSubReturnMtrlLineCountFieldKeys();
        }
        if (billType == NoticeBillType.OTHER_IN || billType == NoticeBillType.OTHER_OUT) {
            return properties.getMiscBillLineCountFieldKeys();
        }
        if (billType == NoticeBillType.SALES_DELIVERY) {
            return properties.getSalesDeliveryLineCountFieldKeys();
        }
        if (billType == NoticeBillType.SALES_RETURN) {
            return properties.getSalReturnNoticeLineCountFieldKeys();
        }
        if (billType == NoticeBillType.PURCHASE_RETURN) {
            return properties.getPurMrbLineCountFieldKeys();
        }
        return properties.getReceiveBillLineCountFieldKeys();
    }

    private String resolveDetailFieldKeys(NoticeBillType billType) {
        if (billType == NoticeBillType.PRODUCTION_ISSUE) {
            return properties.getPickMtrlDetailFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_FEED) {
            return properties.getFeedMtrlDetailFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return properties.getReturnMtrlDetailFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_IN) {
            return properties.getPrdMorptDetailFieldKeys();
        }
        if (billType == NoticeBillType.PRODUCTION_RET_STOCK) {
            return properties.getPrdRetStockDetailFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            return properties.getSubPickMtrlDetailFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_FEED) {
            return properties.getSubFeedMtrlDetailFieldKeys();
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return properties.getSubReturnMtrlDetailFieldKeys();
        }
        if (billType == NoticeBillType.OTHER_IN || billType == NoticeBillType.OTHER_OUT) {
            return properties.getMiscBillDetailFieldKeys();
        }
        if (billType == NoticeBillType.SALES_DELIVERY) {
            return properties.getSalesDeliveryDetailFieldKeys();
        }
        if (billType == NoticeBillType.SALES_RETURN) {
            return properties.getSalReturnNoticeDetailFieldKeys();
        }
        if (billType == NoticeBillType.PURCHASE_RETURN) {
            return properties.getPurMrbDetailFieldKeys();
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
        if (billType == NoticeBillType.PRODUCTION_ISSUE
                || billType == NoticeBillType.PRODUCTION_FEED) {
            // 补料与领料分录字段布局一致（实发/申请）
            return buildPickMtrlFromDetailRows(rows, billNo);
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return buildReturnMtrlFromDetailRows(rows, billNo);
        }
        if (billType == NoticeBillType.PRODUCTION_IN) {
            // 扫已审核生产汇报单：合格 − 入库选单 = 可入量
            return buildPrdMorptFromDetailRows(rows, billNo);
        }
        if (billType == NoticeBillType.PRODUCTION_RET_STOCK) {
            // 入库/退库分录字段布局一致（应收/应退 FMustQty，实收/实退 FRealQty）
            return buildPrdInStockFromDetailRows(rows, billNo);
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE
                || billType == NoticeBillType.OUTSOURCE_FEED) {
            return buildSubPickMtrlFromDetailRows(rows, billNo);
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return buildSubReturnMtrlFromDetailRows(rows, billNo);
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
     * 生产领料单 Query 回退：FieldKeys 见 pickMtrlDetailFieldKeys。
     * 索引约定：数量 10=实发 11=申请 12=基本实发；25/26=建单人编码/姓名。
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
                        .creatorKdUserNumber(cell(row, 25))
                        .creatorName(cell(row, 26))
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = firstNonBlank(cell(row, 8), cell(row, 6));
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal actualQty = parseDecimal(cell(row, 10));
            BigDecimal appQty = parseDecimal(cell(row, 11));
            BigDecimal baseQty = parseDecimal(cell(row, 12));
            // 申请优先；申请空时回退基本申请/实发，避免补料计划全 0
            BigDecimal planQty = firstPositiveDecimal(appQty, baseQty, actualQty);
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
                    // 用实发数量对齐 WMS 已领进度（部分领料回写后可续盘）
                    .inStockJoinBaseQty(actualQty)
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

    private static BigDecimal firstPositiveDecimal(BigDecimal... values) {
        if (values == null) {
            return BigDecimal.ZERO;
        }
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 生产退料单 Query：FieldKeys 见 returnMtrlDetailFieldKeys。
     * 数量 10=实退 FQty、11=申请 FAPPQty、12=基本数量；未审核单计划取申请数量。
     */
    private KingdeeReceiveBillVo buildReturnMtrlFromDetailRows(List<List<String>> rows, String billNo) {
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
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = firstNonBlank(cell(row, 8), cell(row, 6));
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal actualQty = parseDecimal(cell(row, 10));
            BigDecimal appQty = parseDecimal(cell(row, 11));
            // 默认计划=申请；金蝶已改小实退（0<实退<申请）时以实退为目标可处理量
            BigDecimal planQty = resolveReturnPlanQty(appQty, actualQty);
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
     * 生产汇报单 Query：FieldKeys 见 prdMorptDetailFieldKeys。
     * 8=合格 FQuaQty、9=合格品入库选单 FStockInSelQty；计划=合格−选单（可入量，禁止叠计划）。
     */
    private KingdeeReceiveBillVo buildPrdMorptFromDetailRows(List<List<String>> rows, String billNo) {
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
                        .moBillNo(firstNonBlank(cell(row, 5), cell(row, 16)))
                        .warehouseCode(cell(row, 15))
                        .documentStatus("C")
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = cell(row, 6);
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal quaQty = parseDecimal(cell(row, 8));
            BigDecimal stockInSelQty = parseDecimal(cell(row, 9));
            BigDecimal baseQuaQty = parseDecimal(cell(row, 10));
            if (quaQty.compareTo(BigDecimal.ZERO) <= 0 && baseQuaQty.compareTo(BigDecimal.ZERO) > 0) {
                quaQty = baseQuaQty;
            }
            if (stockInSelQty.compareTo(BigDecimal.ZERO) < 0) {
                stockInSelQty = BigDecimal.ZERO;
            }
            BigDecimal remain = quaQty.subtract(stockInSelQty);
            if (remain.compareTo(BigDecimal.ZERO) < 0) {
                remain = BigDecimal.ZERO;
            }
            // 无可入量的行跳过，避免列表噪音；仍允许 View 全量时本地会话已存在的行刷新
            if (remain.compareTo(BigDecimal.ZERO) <= 0 && quaQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int seq = parseInt(cell(row, 11));
            if (seq <= 0) {
                seq = bill.getLines().size() + 1;
            }
            Long entryId = parseLong(cell(row, 12));
            BigDecimal plan = quaQty.compareTo(BigDecimal.ZERO) > 0 ? quaQty : remain;
            bill.getLines().add(KingdeeReceiveBillLineVo.builder()
                    .lineNo(seq)
                    .materialCode(materialCode.trim())
                    .materialName(cell(row, 7))
                    .batchNo(cell(row, 13))
                    .unitCode(cell(row, 14))
                    .stockWarehouseCode(cell(row, 15))
                    .entryId(entryId)
                    // 计划固定为合格数量；已入选单量走 inStockJoin，由 PDA 算可入=计划−已处理
                    .planQty(plan)
                    .qualifiedQty(plan)
                    .baseUnitQty(quaQty.compareTo(BigDecimal.ZERO) > 0 ? quaQty : baseQuaQty)
                    .inStockJoinBaseQty(stockInSelQty)
                    .remainInStockBaseQty(remain)
                    .moBillNo(firstNonBlank(cell(row, 5), cell(row, 16), bill.getMoBillNo()))
                    .moId(parseLong(cell(row, 17)))
                    .moEntryId(parseLong(cell(row, 18)))
                    .moEntrySeq(parseInt(cell(row, 19)) > 0 ? parseInt(cell(row, 19)) : null)
                    .workShopCode(firstNonBlank(cell(row, 3), bill.getSupplierCode()))
                    .receivedQty(BigDecimal.ZERO)
                    .build());
        }
        if (bill != null) {
            bill.setTotalLines(bill.getLines().size());
        }
        return bill != null && billNo.equalsIgnoreCase(bill.getBillNo()) ? bill : bill;
    }

    /**
     * 生产入库单 Query：FieldKeys 见 prdInStockDetailFieldKeys。
     * 数量 8=实收 FRealQty、9=应收 FMustQty；未审核单计划取应收数量。
     */
    private KingdeeReceiveBillVo buildPrdInStockFromDetailRows(List<List<String>> rows, String billNo) {
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
                        .moBillNo(firstNonBlank(cell(row, 5), cell(row, 16)))
                        .warehouseCode(cell(row, 15))
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = cell(row, 6);
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal realQty = parseDecimal(cell(row, 8));
            BigDecimal mustQty = parseDecimal(cell(row, 9));
            BigDecimal baseRealQty = parseDecimal(cell(row, 10));
            // 未审核生产入库：优先应收；应收为 0 时回退实收/基本实收，避免计划全 0 无法扫码
            BigDecimal planQty = mustQty.compareTo(BigDecimal.ZERO) > 0 ? mustQty : realQty;
            if (planQty.compareTo(BigDecimal.ZERO) <= 0 && baseRealQty.compareTo(BigDecimal.ZERO) > 0) {
                planQty = baseRealQty;
            }
            int seq = parseInt(cell(row, 11));
            if (seq <= 0) {
                seq = bill.getLines().size() + 1;
            }
            Long entryId = parseLong(cell(row, 12));
            bill.getLines().add(KingdeeReceiveBillLineVo.builder()
                    .lineNo(seq)
                    .materialCode(materialCode.trim())
                    .materialName(cell(row, 7))
                    .batchNo(cell(row, 13))
                    .unitCode(cell(row, 14))
                    .stockWarehouseCode(cell(row, 15))
                    .entryId(entryId)
                    .planQty(planQty)
                    .moBillNo(firstNonBlank(cell(row, 5), cell(row, 16), bill.getMoBillNo()))
                    .moId(parseLong(cell(row, 17)))
                    .moEntryId(parseLong(cell(row, 18)))
                    .moEntrySeq(parseInt(cell(row, 19)) > 0 ? parseInt(cell(row, 19)) : null)
                    .receivedQty(BigDecimal.ZERO)
                    .build());
        }
        if (bill != null) {
            bill.setTotalLines(bill.getLines().size());
        }
        return bill != null && billNo.equalsIgnoreCase(bill.getBillNo()) ? bill : bill;
    }

    /**
     * 委外领料单 Query：FieldKeys 见 subPickMtrlDetailFieldKeys。
     * 索引约定与生产领料单一致，数量 10=实发 11=申请；未审核单计划取申请数量。
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
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = firstNonBlank(cell(row, 8), cell(row, 6));
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal actualQty = parseDecimal(cell(row, 10));
            BigDecimal appQty = parseDecimal(cell(row, 11));
            BigDecimal baseQty = parseDecimal(cell(row, 12));
            BigDecimal planQty = firstPositiveDecimal(appQty, baseQty, actualQty);
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
                    .inStockJoinBaseQty(actualQty)
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
     * 退料计划量：申请优先；若金蝶已将实退改小于申请，则以实退为目标（便于分批退满后审核）。
     */
    private static BigDecimal resolveReturnPlanQty(BigDecimal appQty, BigDecimal actualQty) {
        BigDecimal app = appQty != null ? appQty : BigDecimal.ZERO;
        BigDecimal actual = actualQty != null ? actualQty : BigDecimal.ZERO;
        if (actual.compareTo(BigDecimal.ZERO) > 0
                && app.compareTo(BigDecimal.ZERO) > 0
                && actual.compareTo(app) < 0) {
            return actual;
        }
        if (app.compareTo(BigDecimal.ZERO) > 0) {
            return app;
        }
        return actual;
    }

    /**
     * 委外退料单 Query：FieldKeys 见 subReturnMtrlDetailFieldKeys。
     * 数量 10=实退 FQty、11=申请 FAPPQty；未审核单计划取申请数量。
     */
    private KingdeeReceiveBillVo buildSubReturnMtrlFromDetailRows(List<List<String>> rows, String billNo) {
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
                        .lines(new ArrayList<>())
                        .build();
            }
            String materialCode = firstNonBlank(cell(row, 8), cell(row, 6));
            if (!StringUtils.hasText(materialCode)) {
                continue;
            }
            BigDecimal actualQty = parseDecimal(cell(row, 10));
            BigDecimal appQty = parseDecimal(cell(row, 11));
            BigDecimal planQty = resolveReturnPlanQty(appQty, actualQty);
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

    /**
     * 金蝶未审核：暂存 Z / 创建 A / 审核中 B / 重新审核 D。
     * 使用 or 而非 in，兼容部分账套 FilterString 对 in 解析失败导致 0 行。
     */
    private static String unauditedDocumentStatusFilter() {
        return "(FDocumentStatus='Z' or FDocumentStatus='A' or FDocumentStatus='B' or FDocumentStatus='D')";
    }

    private static boolean isUnauditedDocumentStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }
        String s = status.trim().toUpperCase();
        return "Z".equals(s) || "A".equals(s) || "B".equals(s) || "D".equals(s);
    }

    private static boolean isSearchKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return false;
        }
        String raw = keyword.trim();
        if (raw.startsWith("{") || raw.contains("://") || raw.length() > 64) {
            return false;
        }
        return true;
    }

    /**
     * 列表过滤。销售发货通知在已审核基础上只保留未出库数量不等于 0 的分录所在单据。
     * 明细扫码打开仍走 {@link #buildFilter}，不因已出完而无法查单。
     */
    private String buildListFilter(NoticeBillType billType, String keyword) {
        String filter = buildFilter(billType, keyword);
        if (billType != null && billType.isOpenRemainOutQtyListBill()) {
            filter += " and FRemainOutQty<>0";
        }
        return filter;
    }

    private List<List<String>> queryApprovedListRows(NoticeBillType billType, String formId, String fieldKeys,
                                                     String filter, String keyword) {
        try {
            return kingdeeCloudService.executeBillQuery(
                    formId, fieldKeys, filter, "FBillNo", 0, resolveListQueryLimit());
        } catch (BusinessException ex) {
            if (billType == null || !billType.isOpenRemainOutQtyListBill()) {
                throw ex;
            }
            log.warn("Kingdee remain-qty list filter failed, fallback Java filter billType={} msg={}",
                    billType.getCode(), ex.getMessage());
            return kingdeeCloudService.executeBillQuery(
                    formId, fieldKeys, buildFilter(billType, keyword), "FBillNo", 0, resolveListQueryLimit());
        }
    }

    static boolean isNonZeroRemainOutQty(String raw) {
        if (!StringUtils.hasText(raw)) {
            // 金蝶 Filter 已限制；字段未返回时不误杀
            return true;
        }
        try {
            return new BigDecimal(raw.trim()).compareTo(BigDecimal.ZERO) != 0;
        } catch (Exception e) {
            return true;
        }
    }

    private String buildFilter(NoticeBillType billType, String keyword) {
        String filter;
        if (billType.isSubmittedInProcessListBill() || billType.isUnauditedWorkflowBill()) {
            filter = unauditedDocumentStatusFilter();
        } else {
            filter = "FDocumentStatus='C'";
        }
        if (StringUtils.hasText(billType.getExtraFilter())) {
            filter += " and " + billType.getExtraFilter();
        }
        if (isSearchKeyword(keyword)) {
            String kw = escapeFilter(keyword.trim());
            if (billType == NoticeBillType.PRODUCTION_ISSUE
                    || billType == NoticeBillType.PRODUCTION_FEED
                    || billType == NoticeBillType.PRODUCTION_RETURN
                    || billType == NoticeBillType.PRODUCTION_IN
                    || billType == NoticeBillType.PRODUCTION_RET_STOCK) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FWorkShopId.FName like '%" + kw + "%'"
                        + " or FWorkShopId.FNumber like '%" + kw + "%'"
                        + " or FEntity_FMoBillNo like '%" + kw + "%')";
            } else if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FSupplierId.FName like '%" + kw + "%'"
                        + " or FSupplierId.FNumber like '%" + kw + "%'"
                        + " or FEntity_FSubReqBillNo like '%" + kw + "%')";
            } else if (billType == NoticeBillType.OUTSOURCE_FEED
                    || billType == NoticeBillType.OUTSOURCE_RETURN) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FSubSupplierId.FName like '%" + kw + "%'"
                        + " or FSubSupplierId.FNumber like '%" + kw + "%'"
                        + " or FEntity_FSubReqBillNo like '%" + kw + "%')";
            } else if (billType == NoticeBillType.OTHER_IN
                    || billType == NoticeBillType.OTHER_OUT) {
                filter += " and FBillNo like '%" + kw + "%'";
            } else if (billType == NoticeBillType.SALES_DELIVERY) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FCustomerID.FName like '%" + kw + "%'"
                        + " or FCustomerID.FNumber like '%" + kw + "%')";
            } else if (billType == NoticeBillType.SALES_RETURN) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FRetcustId.FName like '%" + kw + "%'"
                        + " or FRetcustId.FNumber like '%" + kw + "%')";
            } else if (billType == NoticeBillType.PURCHASE_RETURN) {
                filter += " and (FBillNo like '%" + kw + "%'"
                        + " or FSupplierId.FName like '%" + kw + "%'"
                        + " or FSupplierId.FNumber like '%" + kw + "%')";
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
        if (billType == NoticeBillType.PRODUCTION_ISSUE) {
            return properties.getPickMtrlFormId();
        }
        if (billType == NoticeBillType.PRODUCTION_FEED) {
            return properties.getFeedMtrlFormId();
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return properties.getReturnMtrlFormId();
        }
        if (billType == NoticeBillType.PRODUCTION_IN) {
            return properties.getPrdMorptFormId();
        }
        if (billType == NoticeBillType.PRODUCTION_RET_STOCK) {
            return properties.getPrdRetStockFormId();
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            return properties.getSubPickMtrlFormId();
        }
        if (billType == NoticeBillType.OUTSOURCE_FEED) {
            return properties.getSubFeedMtrlFormId();
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return properties.getSubReturnMtrlFormId();
        }
        if (billType == NoticeBillType.OTHER_IN) {
            return properties.getMiscInStockFormId();
        }
        if (billType == NoticeBillType.OTHER_OUT) {
            return properties.getMisDeliveryFormId();
        }
        if (billType == NoticeBillType.PURCHASE_RETURN) {
            return properties.getPurMrbFormId();
        }
        if (billType == NoticeBillType.SALES_RETURN) {
            return properties.getSalReturnNoticeFormId();
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
        if (billType == NoticeBillType.PRODUCTION_FEED) {
            return List.of(productionFeedMockBill());
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            return List.of(outsourceIssueMockBill());
        }
        if (billType == NoticeBillType.OUTSOURCE_FEED) {
            return List.of(outsourceFeedMockBill());
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return List.of(productionReturnMockBill());
        }
        if (billType == NoticeBillType.PRODUCTION_IN) {
            return List.of(productionInMockBill());
        }
        if (billType == NoticeBillType.PRODUCTION_RET_STOCK) {
            return List.of(productionRetStockMockBill());
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return List.of(outsourceReturnMockBill());
        }
        String prefix = switch (billType) {
            case PRODUCTION_IN -> "SCR";
            case PRODUCTION_RET_STOCK -> "STK";
            case PRODUCTION_RETURN -> "SCT";
            case OUTSOURCE_RETURN -> "WWT";
            case OTHER_IN -> "QTR";
            case SALES_DELIVERY -> "XSF";
            case SALES_RETURN -> "XST";
            case PRODUCTION_ISSUE -> "SCL";
            case PRODUCTION_FEED -> "SCB";
            case OUTSOURCE_ISSUE -> "WWC";
            case OUTSOURCE_FEED -> "WWB";
            case OTHER_OUT -> "QTC";
            case PURCHASE_RETURN -> "CGT";
            default -> "SLD";
        };
        return List.of(
                KingdeeReceiveBillVo.builder()
                        .billNo(prefix + "20260708001")
                        .billDate(LocalDate.now())
                        .supplierCode("SUP001")
                        .supplierName(billType.getLabel() + "-模拟客户/供应商")
                        .documentStatus(billType.isUnauditedWorkflowBill() ? "A" : "C")
                        .warehouseCode("CK004")
                        .totalLines(2)
                        .lines(List.of(
                                line(1, "MAT-10001", "模拟物料A", "PCS", "100"),
                                line(2, "MAT-10002", "模拟物料B", "PCS", "50")))
                        .build());
    }

    /** 生产退料联调：扫未审核生产退料单 SCT20260715001 */
    private KingdeeReceiveBillVo productionReturnMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("SCT20260715001")
                .billId(90017001L)
                .billDate(LocalDate.now())
                .supplierCode("WS01")
                .supplierName("一车间")
                .moBillNo("MO20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("A")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        pickLine(1, 90017011L, "PITEST-001", "螺栓 M8", "M8×20", "B20260715", "PCS", "100", "CK004"),
                        pickLine(2, 90017012L, "PITEST-002", "垫片 Φ8", "Φ8×1.5", "B20260715", "PCS", "50", "CK004")))
                .build();
    }

    /** 生产汇报入库联调：扫已审核生产汇报单 MORPT20260715001（合格−选单=可入） */
    private KingdeeReceiveBillVo productionInMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("MORPT20260715001")
                .billId(90019001L)
                .billDate(LocalDate.now())
                .supplierCode("WS01")
                .supplierName("一车间")
                .moBillNo("MO20260715001")
                .documentStatus("C")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        morptLine(1, 90019011L, "FG-TEST-001", "成品A", "规格A", "B20260715", "PCS",
                                "100", "20", "CK004"),
                        morptLine(2, 90019012L, "FG-TEST-002", "成品B", "规格B", "B20260715", "PCS",
                                "50", "0", "CK004")))
                .build();
    }

    private KingdeeReceiveBillLineVo morptLine(int no, long entryId, String code, String name, String spec,
                                               String batchNo, String unit, String quaQty, String stockInSelQty,
                                               String stockCode) {
        BigDecimal qua = new BigDecimal(quaQty);
        BigDecimal joined = new BigDecimal(stockInSelQty);
        BigDecimal remain = qua.subtract(joined);
        if (remain.compareTo(BigDecimal.ZERO) < 0) {
            remain = BigDecimal.ZERO;
        }
        return KingdeeReceiveBillLineVo.builder()
                .lineNo(no)
                .materialCode(code)
                .materialName(name)
                .specification(spec)
                .batchNo(batchNo)
                .planQty(qua)
                .qualifiedQty(qua)
                .baseUnitQty(qua)
                .inStockJoinBaseQty(joined)
                .remainInStockBaseQty(remain)
                .unitCode(unit)
                .stockWarehouseCode(stockCode)
                .entryId(entryId)
                .moBillNo("MO20260715001")
                .moId(80015001L)
                .moEntryId(80015011L)
                .moEntrySeq(1)
                .receivedQty(BigDecimal.ZERO)
                .build();
    }

    /** 生产退库联调：扫未审核生产退库单 STK20260715001 */
    private KingdeeReceiveBillVo productionRetStockMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("STK20260715001")
                .billId(90020001L)
                .billDate(LocalDate.now())
                .supplierCode("WS01")
                .supplierName("一车间")
                .moBillNo("MO20260715001")
                .documentStatus("A")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        pickLine(1, 90020011L, "FG-TEST-001", "成品A", "规格A", "B20260715", "PCS", "20", "CK004"),
                        pickLine(2, 90020012L, "FG-TEST-002", "成品B", "规格B", "B20260715", "PCS", "10", "CK004")))
                .build();
    }

    /** 委外退料联调：扫未审核委外退料单 WWT20260715001 */
    private KingdeeReceiveBillVo outsourceReturnMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("WWT20260715001")
                .billId(90018001L)
                .billDate(LocalDate.now())
                .supplierCode("SUP001")
                .supplierName("模拟委外供应商")
                .moBillNo("WWDD20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("A")
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

    /** 生产领料联调：未审核领料单 SCL20260715001 */
    private KingdeeReceiveBillVo productionIssueMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("SCL20260715001")
                .billId(90016001L)
                .billDate(LocalDate.now())
                .supplierCode("WS01")
                .supplierName("一车间")
                .moBillNo("MO20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("B")
                .creatorKdUserNumber("admin")
                .creatorName("管理员")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        pickLine(1, 90016011L, "PITEST-001", "螺栓 M8", "M8×20", "B20260715", "PCS", "100", "CK004"),
                        pickLine(2, 90016012L, "PITEST-002", "垫片 Φ8", "Φ8×1.5", "B20260715", "PCS", "50", "CK004")))
                .build();
    }

    /** 生产补料联调：未审核补料单 SCB20260715001 */
    private KingdeeReceiveBillVo productionFeedMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("SCB20260715001")
                .billId(90019001L)
                .billDate(LocalDate.now())
                .supplierCode("WS01")
                .supplierName("一车间")
                .moBillNo("MO20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("A")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        pickLine(1, 90019011L, "PITEST-001", "螺栓 M8", "M8×20", "B20260715", "PCS", "30", "CK004"),
                        pickLine(2, 90019012L, "PITEST-002", "垫片 Φ8", "Φ8×1.5", "B20260715", "PCS", "20", "CK004")))
                .build();
    }

    /** 委外领料联调：未审核领料单 WWL20260715001 */
    private KingdeeReceiveBillVo outsourceIssueMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("WWL20260715001")
                .billId(90017001L)
                .billDate(LocalDate.now())
                .supplierCode("SUP001")
                .supplierName("模拟委外供应商")
                .moBillNo("WWDD20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("A")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        subPickLine(1, 90017011L, "PITEST-001", "螺栓 M8", "M8×20", "B20260715", "PCS", "100", "CK004"),
                        subPickLine(2, 90017012L, "PITEST-002", "垫片 Φ8", "Φ8×1.5", "B20260715", "PCS", "50", "CK004")))
                .build();
    }

    /** 委外补料联调：未审核补料单 WWB20260715001 */
    private KingdeeReceiveBillVo outsourceFeedMockBill() {
        return KingdeeReceiveBillVo.builder()
                .billNo("WWB20260715001")
                .billId(90020001L)
                .billDate(LocalDate.now())
                .supplierCode("SUP001")
                .supplierName("模拟委外供应商")
                .moBillNo("WWDD20260715001")
                .parentMaterialCode("FG-TEST-001")
                .documentStatus("A")
                .warehouseCode("CK004")
                .totalLines(2)
                .lines(List.of(
                        subPickLine(1, 90020011L, "PITEST-001", "螺栓 M8", "M8×20", "B20260715", "PCS", "30", "CK004"),
                        subPickLine(2, 90020012L, "PITEST-002", "垫片 Φ8", "Φ8×1.5", "B20260715", "PCS", "20", "CK004")))
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
