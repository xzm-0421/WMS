package com.wms.pdastockcount.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wms.auth.security.LoginUser;
import com.wms.barcode.dto.BarcodeRecognizeResult;
import com.wms.barcode.service.BarcodeRecognizeService;
import com.wms.common.cache.PdaShortCache;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.config.WmsPdaProperties;
import com.wms.integration.kingdee.KingdeeStockCountService;
import com.wms.integration.kingdee.dto.KingdeeStockCountBillVo;
import com.wms.integration.kingdee.dto.KingdeeStockCountLineVo;
import com.wms.pdastockcount.dto.StockCountDetailVo;
import com.wms.pdastockcount.dto.StockCountLineVo;
import com.wms.pdastockcount.dto.StockCountListItemVo;
import com.wms.pdastockcount.dto.StockCountQtyRequest;
import com.wms.pdastockcount.dto.StockCountScanRequest;
import com.wms.pdastockcount.entity.PdaStockCountLine;
import com.wms.pdastockcount.entity.PdaStockCountSession;
import com.wms.pdastockcount.mapper.PdaStockCountLineMapper;
import com.wms.pdastockcount.mapper.PdaStockCountSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * PDA 金蝶盘点作业：拉取已审核明细、扫码匹配、实盘录入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdaStockCountService {

    private final KingdeeStockCountService kingdeeStockCountService;
    private final PdaStockCountSessionMapper sessionMapper;
    private final PdaStockCountLineMapper lineMapper;
    private final BarcodeRecognizeService barcodeRecognizeService;
    private final WmsPdaProperties pdaProperties;
    private final PdaShortCache pdaShortCache;

    public PageResult<StockCountListItemVo> list(String keyword, long current, long size) {
        long page = Math.max(1, current);
        long pageSize = Math.max(1, Math.min(size, 100));
        String kwKey = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : "";
        String cacheKey = "stockcount-list-visible:" + kwKey;

        List<StockCountListItemVo> visible = null;
        if (pdaProperties.getShortCacheTtlSeconds() > 0) {
            visible = pdaShortCache.get(cacheKey, new TypeReference<List<StockCountListItemVo>>() {});
        }
        if (visible == null) {
            int fetchSize = Math.min(Math.max(pdaProperties.getListFetchLimit() * 5, 500), 2000);
            PageResult<KingdeeStockCountBillVo> kdPage = kingdeeStockCountService.pageBills(keyword, 1, fetchSize);
            List<KingdeeStockCountBillVo> kdRecords = kdPage.getRecords() != null ? kdPage.getRecords() : List.of();

            List<String> billNos = kdRecords.stream()
                    .filter(b -> b != null && StringUtils.hasText(b.getBillNo()))
                    .map(b -> b.getBillNo().trim())
                    .distinct()
                    .collect(Collectors.toList());
            Map<String, PdaStockCountSession> sessionMap = loadSessions(billNos);

            Map<String, StockCountListItemVo> merged = new LinkedHashMap<>();
            for (KingdeeStockCountBillVo bill : kdRecords) {
                if (bill == null || !StringUtils.hasText(bill.getBillNo())) {
                    continue;
                }
                String no = bill.getBillNo().trim();
                PdaStockCountSession session = sessionMap.get(no);
                if (session != null && "COMPLETED".equals(session.getStatus())) {
                    continue;
                }
                merged.put(no, toListItem(bill, session));
            }

            List<PdaStockCountSession> openSessions = sessionMapper.selectList(new LambdaQueryWrapper<PdaStockCountSession>()
                    .eq(PdaStockCountSession::getStatus, "COUNTING")
                    .orderByDesc(PdaStockCountSession::getUpdateTime));
            for (PdaStockCountSession session : openSessions) {
                if (!StringUtils.hasText(session.getBillNo()) || merged.containsKey(session.getBillNo())) {
                    continue;
                }
                if (!kingdeeStockCountService.isKingdeeEnabled()) {
                    merged.put(session.getBillNo(), toListItemFromSession(session));
                }
            }

            visible = new ArrayList<>(merged.values());
            visible.sort(Comparator
                    .comparing(StockCountListItemVo::getBillDate, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(StockCountListItemVo::getBillNo, Comparator.nullsLast(Comparator.reverseOrder())));
            if (pdaProperties.getShortCacheTtlSeconds() > 0) {
                pdaShortCache.put(cacheKey, visible,
                        Duration.ofSeconds(Math.max(60, pdaProperties.getShortCacheTtlSeconds())));
            }
        }

        long total = visible.size();
        int from = (int) Math.max(0, (page - 1) * pageSize);
        int to = (int) Math.min(visible.size(), from + (int) pageSize);
        List<StockCountListItemVo> slice = from < visible.size() ? visible.subList(from, to) : List.of();
        return PageResult.of(slice, total, page, pageSize);
    }

    public String resolveBillNoFromBarcode(String barcode) {
        return kingdeeStockCountService.parseBillNo(barcode);
    }

    public StockCountDetailVo getDetail(String billNo) {
        return getDetail(billNo, false);
    }

    @Transactional
    public StockCountDetailVo getDetail(String billNo, boolean forceRefresh) {
        String no = billNo != null ? billNo.trim() : "";
        if (!StringUtils.hasText(no)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "盘点单号不能为空");
        }
        PdaStockCountSession session = loadSession(no);
        List<PdaStockCountLine> localLines = loadLines(no);
        if (!forceRefresh && session != null && localLines != null && !localLines.isEmpty()) {
            return buildDetail(session, localLines);
        }

        KingdeeStockCountBillVo bill;
        try {
            bill = kingdeeStockCountService.getBill(no);
        } catch (BusinessException ex) {
            if (session != null && localLines != null && !localLines.isEmpty()) {
                log.warn("金蝶拉取盘点单失败，使用本地会话 billNo={} msg={}", no, ex.getMessage());
                return buildDetail(session, localLines);
            }
            throw ex;
        } catch (Exception e) {
            if (session != null && localLines != null && !localLines.isEmpty()) {
                log.warn("金蝶拉取盘点单异常，使用本地会话 billNo={}", no, e);
                return buildDetail(session, localLines);
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "拉取盘点单失败，请检查网络后重试");
        }
        if (bill == null) {
            if (session != null && localLines != null && !localLines.isEmpty()) {
                log.warn("金蝶拉取盘点单为空，使用本地会话 billNo={}", no);
                return buildDetail(session, localLines);
            }
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到已审核的盘点作业单: " + no);
        }
        if (StringUtils.hasText(bill.getDocumentStatus()) && !"C".equalsIgnoreCase(bill.getDocumentStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "仅支持已审核的盘点作业单，当前状态: " + bill.getDocumentStatus());
        }
        session = ensureSession(bill, session);
        localLines = syncLinesFromKingdee(session, bill, localLines);
        return buildDetail(session, localLines);
    }

    /**
     * 扫码匹配明细（不落库），用于 PDA 实时反馈。
     */
    public StockCountLineVo match(String billNo, StockCountScanRequest request) {
        StockCountDetailVo detail = getDetail(billNo);
        List<PdaStockCountLine> lines = loadLines(detail.getBillNo());
        String materialCode = request != null ? request.getMaterialCode() : null;
        String batchNo = request != null ? request.getBatchNo() : null;
        String locationCode = request != null ? request.getLocationCode() : null;
        String barcode = request != null ? request.getBarcodeContent() : null;
        BigDecimal qtyHint = null;

        if (StringUtils.hasText(barcode)) {
            try {
                BarcodeRecognizeResult recognized = barcodeRecognizeService.recognize(barcode.trim());
                if (recognized != null) {
                    if (!StringUtils.hasText(materialCode) && StringUtils.hasText(recognized.getMaterialCode())) {
                        materialCode = recognized.getMaterialCode().trim();
                    }
                    if (!StringUtils.hasText(batchNo) && StringUtils.hasText(recognized.getBatchNo())) {
                        batchNo = recognized.getBatchNo().trim();
                    }
                    if (!StringUtils.hasText(locationCode) && StringUtils.hasText(recognized.getLocationCode())) {
                        locationCode = recognized.getLocationCode().trim();
                    }
                    qtyHint = recognized.getQuantity();
                }
            } catch (BusinessException ex) {
                log.info("盘点匹配条码未命中 barcode={}: {}", barcode, ex.getMessage());
            } catch (Exception e) {
                log.warn("盘点匹配条码异常 barcode={}", barcode, e);
            }
        }
        if (!StringUtils.hasText(materialCode) && StringUtils.hasText(barcode)) {
            materialCode = barcode.trim();
        }
        if (request != null && request.getLineNo() != null) {
            PdaStockCountLine byLine = lines.stream()
                    .filter(l -> Objects.equals(l.getLineNo(), request.getLineNo()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "明细行不存在: " + request.getLineNo()));
            StockCountLineVo vo = toLineVo(byLine);
            if (qtyHint != null && vo.getActualQty() == null) {
                vo.setActualQty(qtyHint);
            }
            return vo;
        }
        PdaStockCountLine target = matchLine(lines, materialCode, batchNo, locationCode);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    buildMatchFailMessage(materialCode, batchNo, locationCode));
        }
        StockCountLineVo vo = toLineVo(target);
        if (qtyHint != null && vo.getActualQty() == null) {
            vo.setActualQty(qtyHint);
        }
        return vo;
    }

    /**
     * 扫码匹配明细并录入实盘数量；也可按 lineNo 直接提交。
     */
    @Transactional
    public StockCountLineVo scan(String billNo, StockCountScanRequest request) {
        if (request == null || request.getActualQty() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实盘数量不能为空");
        }
        if (request.getActualQty().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实盘数量不能为负");
        }
        StockCountDetailVo detail = getDetail(billNo);
        PdaStockCountSession session = loadSession(detail.getBillNo());
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "盘点会话不存在");
        }
        if ("COMPLETED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "盘点已完成，不可再录入");
        }

        List<PdaStockCountLine> lines = loadLines(session.getBillNo());
        PdaStockCountLine target;

        if (request.getLineNo() != null) {
            target = lines.stream()
                    .filter(l -> Objects.equals(l.getLineNo(), request.getLineNo()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "明细行不存在: " + request.getLineNo()));
        } else {
            StockCountLineVo matched = match(billNo, request);
            target = lines.stream()
                    .filter(l -> Objects.equals(l.getLineNo(), matched.getLineNo()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "匹配明细不存在"));
        }

        applyActualQty(session, target, request.getActualQty(), request.getBarcodeContent(), request.getDeviceNo());
        return toLineVo(target);
    }

    @Transactional
    public StockCountLineVo updateQty(String billNo, Integer lineNo, StockCountQtyRequest request) {
        if (request == null || request.getActualQty() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实盘数量不能为空");
        }
        StockCountScanRequest scanRequest = new StockCountScanRequest();
        scanRequest.setLineNo(lineNo);
        scanRequest.setActualQty(request.getActualQty());
        return scan(billNo, scanRequest);
    }

    @Transactional
    public Map<String, Object> complete(String billNo) {
        StockCountDetailVo detail = getDetail(billNo);
        PdaStockCountSession session = loadSession(detail.getBillNo());
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "盘点会话不存在");
        }
        List<PdaStockCountLine> lines = loadLines(session.getBillNo());
        long pending = lines.stream().filter(l -> !"COUNTED".equals(l.getLineStatus())).count();
        if (pending > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "还有 " + pending + " 行未盘点，请完成后再提交");
        }
        session.setStatus("COMPLETED");
        session.setCountedLines(lines.size());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        invalidateListCache();
        return Map.of(
                "billNo", session.getBillNo(),
                "totalLines", session.getTotalLines() != null ? session.getTotalLines() : lines.size(),
                "countedLines", session.getCountedLines());
    }

    private void applyActualQty(PdaStockCountSession session, PdaStockCountLine line,
                                BigDecimal actualQty, String barcode, String deviceNo) {
        boolean wasCounted = "COUNTED".equals(line.getLineStatus());
        line.setActualQty(actualQty);
        line.setLineStatus("COUNTED");
        line.setScannedBarcode(StringUtils.hasText(barcode) ? barcode.trim() : line.getScannedBarcode());
        line.setLastScanTime(LocalDateTime.now());
        line.setUpdateTime(LocalDateTime.now());
        lineMapper.updateById(line);

        if (!wasCounted) {
            int counted = session.getCountedLines() == null ? 0 : session.getCountedLines();
            session.setCountedLines(counted + 1);
        }
        session.setStatus("COUNTING");
        session.setLastScanTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(deviceNo)) {
            session.setDeviceNo(deviceNo.trim());
        }
        fillOperator(session);
        sessionMapper.updateById(session);
    }

    private PdaStockCountLine matchLine(List<PdaStockCountLine> lines, String materialCode,
                                        String batchNo, String locationCode) {
        List<PdaStockCountLine> candidates = lines;
        if (StringUtils.hasText(materialCode)) {
            String mat = materialCode.trim();
            candidates = candidates.stream()
                    .filter(l -> mat.equalsIgnoreCase(l.getMaterialCode()))
                    .collect(Collectors.toList());
        }
        if (StringUtils.hasText(batchNo)) {
            String batch = batchNo.trim();
            List<PdaStockCountLine> byBatch = candidates.stream()
                    .filter(l -> batch.equalsIgnoreCase(nullToEmpty(l.getBatchNo())))
                    .collect(Collectors.toList());
            if (!byBatch.isEmpty()) {
                candidates = byBatch;
            }
        }
        if (StringUtils.hasText(locationCode)) {
            String loc = locationCode.trim();
            List<PdaStockCountLine> byLoc = candidates.stream()
                    .filter(l -> loc.equalsIgnoreCase(nullToEmpty(l.getLocationCode())))
                    .collect(Collectors.toList());
            if (!byLoc.isEmpty()) {
                candidates = byLoc;
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(l -> !"COUNTED".equals(l.getLineStatus()))
                .findFirst()
                .orElse(candidates.get(0));
    }

    private String buildMatchFailMessage(String materialCode, String batchNo, String locationCode) {
        StringBuilder sb = new StringBuilder("未匹配到盘点明细");
        if (StringUtils.hasText(materialCode)) {
            sb.append("，物料 ").append(materialCode);
        }
        if (StringUtils.hasText(batchNo)) {
            sb.append("，批次 ").append(batchNo);
        }
        if (StringUtils.hasText(locationCode)) {
            sb.append("，库位 ").append(locationCode);
        }
        return sb.toString();
    }

    private PdaStockCountSession ensureSession(KingdeeStockCountBillVo bill, PdaStockCountSession existing) {
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            PdaStockCountSession session = new PdaStockCountSession();
            session.setBillNo(bill.getBillNo());
            session.setBillDate(bill.getBillDate());
            session.setWarehouseCode(bill.getWarehouseCode());
            session.setStockOrgCode(bill.getStockOrgCode());
            session.setRemark(bill.getRemark());
            session.setStatus("COUNTING");
            session.setTotalLines(bill.getTotalLines() != null ? bill.getTotalLines()
                    : (bill.getLines() != null ? bill.getLines().size() : 0));
            session.setCountedLines(0);
            session.setCreateTime(now);
            session.setUpdateTime(now);
            fillOperator(session);
            sessionMapper.insert(session);
            return session;
        }
        existing.setBillDate(bill.getBillDate());
        existing.setWarehouseCode(bill.getWarehouseCode());
        existing.setStockOrgCode(bill.getStockOrgCode());
        existing.setRemark(bill.getRemark());
        int total = bill.getTotalLines() != null ? bill.getTotalLines()
                : (bill.getLines() != null ? bill.getLines().size() : existing.getTotalLines());
        existing.setTotalLines(total);
        existing.setUpdateTime(now);
        fillOperator(existing);
        sessionMapper.updateById(existing);
        return existing;
    }

    private List<PdaStockCountLine> syncLinesFromKingdee(PdaStockCountSession session,
                                                         KingdeeStockCountBillVo bill,
                                                         List<PdaStockCountLine> existing) {
        Map<Integer, PdaStockCountLine> existingByLine = existing == null ? Map.of()
                : existing.stream().collect(Collectors.toMap(PdaStockCountLine::getLineNo, l -> l, (a, b) -> a));
        List<KingdeeStockCountLineVo> kdLines = bill.getLines() != null ? bill.getLines() : List.of();
        List<PdaStockCountLine> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int counted = 0;
        for (KingdeeStockCountLineVo kd : kdLines) {
            if (kd == null || !StringUtils.hasText(kd.getMaterialCode())) {
                continue;
            }
            int lineNo = kd.getLineNo() != null ? kd.getLineNo() : result.size() + 1;
            PdaStockCountLine local = existingByLine.get(lineNo);
            if (local == null) {
                local = new PdaStockCountLine();
                local.setSessionId(session.getId());
                local.setBillNo(session.getBillNo());
                local.setLineNo(lineNo);
                local.setLineStatus("PENDING");
                local.setCreateTime(now);
            }
            local.setEntryId(kd.getEntryId());
            local.setMaterialCode(kd.getMaterialCode());
            local.setMaterialName(kd.getMaterialName());
            local.setSpecification(kd.getSpecification());
            local.setWarehouseCode(kd.getWarehouseCode());
            local.setLocationCode(kd.getLocationCode());
            local.setBatchNo(kd.getBatchNo());
            local.setUnitCode(kd.getUnitCode());
            local.setBookQty(kd.getBookQty() != null ? kd.getBookQty() : BigDecimal.ZERO);
            local.setUpdateTime(now);
            if (local.getId() == null) {
                lineMapper.insert(local);
            } else {
                lineMapper.updateById(local);
            }
            if ("COUNTED".equals(local.getLineStatus())) {
                counted++;
            }
            result.add(local);
        }
        session.setTotalLines(result.size());
        session.setCountedLines(counted);
        session.setUpdateTime(now);
        sessionMapper.updateById(session);
        return result;
    }

    private StockCountDetailVo buildDetail(PdaStockCountSession session, List<PdaStockCountLine> lines) {
        List<StockCountLineVo> vos = lines.stream()
                .sorted(Comparator.comparing(PdaStockCountLine::getLineNo, Comparator.nullsLast(Integer::compareTo)))
                .map(this::toLineVo)
                .collect(Collectors.toList());
        return StockCountDetailVo.builder()
                .billNo(session.getBillNo())
                .billDate(session.getBillDate())
                .warehouseCode(session.getWarehouseCode())
                .stockOrgCode(session.getStockOrgCode())
                .remark(session.getRemark())
                .documentStatus("C")
                .scanStatus(session.getStatus())
                .totalLines(session.getTotalLines())
                .countedLines(session.getCountedLines())
                .lines(vos)
                .build();
    }

    private StockCountLineVo toLineVo(PdaStockCountLine line) {
        BigDecimal book = line.getBookQty() != null ? line.getBookQty() : BigDecimal.ZERO;
        BigDecimal actual = line.getActualQty();
        BigDecimal diff = actual != null ? actual.subtract(book) : null;
        return StockCountLineVo.builder()
                .lineNo(line.getLineNo())
                .entryId(line.getEntryId())
                .materialCode(line.getMaterialCode())
                .materialName(line.getMaterialName())
                .specification(line.getSpecification())
                .warehouseCode(line.getWarehouseCode())
                .locationCode(line.getLocationCode())
                .batchNo(line.getBatchNo())
                .unitCode(line.getUnitCode())
                .bookQty(book)
                .actualQty(actual)
                .diffQty(diff)
                .lineStatus(line.getLineStatus())
                .counted("COUNTED".equals(line.getLineStatus()))
                .build();
    }

    private StockCountListItemVo toListItem(KingdeeStockCountBillVo bill, PdaStockCountSession session) {
        int total = bill.getTotalLines() != null ? bill.getTotalLines()
                : (bill.getLines() != null ? bill.getLines().size() : 0);
        int counted = session != null && session.getCountedLines() != null ? session.getCountedLines() : 0;
        String scanStatus = session != null ? session.getStatus() : "NEW";
        return StockCountListItemVo.builder()
                .billNo(bill.getBillNo())
                .billDate(bill.getBillDate())
                .warehouseCode(bill.getWarehouseCode())
                .stockOrgCode(bill.getStockOrgCode())
                .remark(bill.getRemark())
                .documentStatus(StringUtils.hasText(bill.getDocumentStatus()) ? bill.getDocumentStatus() : "C")
                .totalLines(total)
                .countedLines(counted)
                .scanStatus(scanStatus)
                .inProgress(session != null && "COUNTING".equals(session.getStatus()) && counted > 0)
                .build();
    }

    private StockCountListItemVo toListItemFromSession(PdaStockCountSession session) {
        return StockCountListItemVo.builder()
                .billNo(session.getBillNo())
                .billDate(session.getBillDate())
                .warehouseCode(session.getWarehouseCode())
                .stockOrgCode(session.getStockOrgCode())
                .remark(session.getRemark())
                .documentStatus("C")
                .totalLines(session.getTotalLines())
                .countedLines(session.getCountedLines())
                .scanStatus(session.getStatus())
                .inProgress(true)
                .build();
    }

    private Map<String, PdaStockCountSession> loadSessions(List<String> billNos) {
        if (billNos == null || billNos.isEmpty()) {
            return Map.of();
        }
        List<PdaStockCountSession> list = sessionMapper.selectList(new LambdaQueryWrapper<PdaStockCountSession>()
                .in(PdaStockCountSession::getBillNo, billNos));
        Map<String, PdaStockCountSession> map = new LinkedHashMap<>();
        for (PdaStockCountSession s : list) {
            if (s != null && StringUtils.hasText(s.getBillNo())) {
                map.put(s.getBillNo().trim(), s);
            }
        }
        return map;
    }

    private PdaStockCountSession loadSession(String billNo) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<PdaStockCountSession>()
                .eq(PdaStockCountSession::getBillNo, billNo));
    }

    private List<PdaStockCountLine> loadLines(String billNo) {
        return lineMapper.selectList(new LambdaQueryWrapper<PdaStockCountLine>()
                .eq(PdaStockCountLine::getBillNo, billNo)
                .orderByAsc(PdaStockCountLine::getLineNo));
    }

    private void fillOperator(PdaStockCountSession session) {
        try {
            LoginUser user = SecurityUtils.currentUser();
            if (user != null) {
                session.setOperatorId(user.getUserId() != null ? String.valueOf(user.getUserId()) : user.getUsername());
                session.setOperatorName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
            }
        } catch (Exception ignored) {
            // 匿名或非登录场景忽略
        }
    }

    private void invalidateListCache() {
        // 依赖短缓存 TTL 自动失效
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
