package com.wms.pdareceive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.auth.security.LoginUser;
import com.wms.barcode.dto.BarcodeRecognizeResult;
import com.wms.barcode.service.BarcodeRecognizeService;
import com.wms.barcode.util.BatchNoNormalizer;
import com.wms.barcode.service.BarcodeTraceLinkService;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.noticebill.NoticeBillProvider;
import com.wms.noticebill.NoticeBillProviderRegistry;
import com.wms.noticebill.NoticeBillType;
import com.wms.integration.kingdee.KingdeeErpWarehouseResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wms.common.cache.PdaShortCache;
import com.wms.config.WmsPdaProperties;
import com.wms.integration.kingdee.KingdeePickMtrlRequest;
import com.wms.integration.kingdee.KingdeeReturnMtrlRequest;
import com.wms.integration.kingdee.KingdeeSubPickMtrlRequest;
import com.wms.integration.kingdee.KingdeeSubReturnMtrlRequest;
import com.wms.integration.kingdee.KingdeeResponseMessageFormatter;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillLineVo;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.dto.InventoryChangeResult;
import com.wms.inventory.service.InventoryService;
import com.wms.pdainbound.entity.PdaInboundRecord;
import com.wms.pdainbound.mapper.PdaInboundRecordMapper;
import com.wms.pdareceive.dto.*;
import com.wms.pdareceive.entity.PdaReceiveScanLine;
import com.wms.pdareceive.entity.PdaReceiveScanSession;
import com.wms.pdareceive.entity.PdaReceiveSubmitBatch;
import com.wms.pdareceive.mapper.PdaReceiveScanLineMapper;
import com.wms.pdareceive.mapper.PdaReceiveScanSessionMapper;
import com.wms.pdareceive.mapper.PdaReceiveSubmitBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdaReceiveScanService {

    private static final AtomicLong BATCH_SEQ = new AtomicLong(1);
    private static final AtomicLong RECORD_SEQ = new AtomicLong(1);

    private final NoticeBillProviderRegistry noticeBillProviderRegistry;
    private final PdaReceiveScanSessionMapper sessionMapper;
    private final PdaReceiveScanLineMapper lineMapper;
    private final PdaReceiveSubmitBatchMapper submitBatchMapper;
    private final PdaInboundRecordMapper inboundRecordMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryService inventoryService;
    private final BarcodeTraceLinkService barcodeTraceLinkService;
    private final BarcodeRecognizeService barcodeRecognizeService;
    private final PdaReceiveSubmitBatchService submitBatchService;
    private final PdaErpSyncAsyncService erpSyncAsyncService;
    private final KingdeeErpWarehouseResolver erpWarehouseResolver;
    private final WmsPdaProperties pdaProperties;
    private final PdaShortCache pdaShortCache;

    private record BillMaterialMatch(String materialCode, String batchNo, String matchMode) {}

    public PageResult<ReceiveNoticeListItemVo> list(String keyword, long current, long size) {
        return list(NoticeBillType.PURCHASE_RECEIVE, keyword, current, size);
    }

    public PageResult<ReceiveNoticeListItemVo> list(NoticeBillType billType, String keyword, long current, long size) {
        NoticeBillProvider provider = noticeBillProviderRegistry.require(billType);
        long page = Math.max(1, current);
        long pageSize = Math.max(1, Math.min(size, 100));
        String kwKey = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : "";
        String visibleCacheKey = "list-visible:" + billType.getCode() + ":" + kwKey;

        List<ReceiveNoticeListItemVo> visible = null;
        if (pdaProperties.getShortCacheTtlSeconds() > 0) {
            visible = pdaShortCache.get(visibleCacheKey, new TypeReference<List<ReceiveNoticeListItemVo>>() {});
        }
        if (visible == null) {
            // 一次性取够后续翻页用的数据（金蝶侧已有短缓存），再在本地会话过滤后分页
            int fetchSize = Math.max(pdaProperties.getListFetchLimit() * 5, 500);
            fetchSize = Math.min(fetchSize, 2000);
            PageResult<KingdeeReceiveBillVo> kdPage = provider.pageBills(keyword, 1, fetchSize);
            List<KingdeeReceiveBillVo> kdRecords = kdPage.getRecords() != null ? kdPage.getRecords() : List.of();

            List<String> billNos = kdRecords.stream()
                    .filter(b -> b != null && StringUtils.hasText(b.getBillNo()))
                    .map(b -> b.getBillNo().trim())
                    .distinct()
                    .collect(Collectors.toList());
            Map<String, PdaReceiveScanSession> sessionMap = loadSessions(billType, billNos);

            Map<String, ReceiveNoticeListItemVo> merged = new LinkedHashMap<>();
            for (KingdeeReceiveBillVo bill : kdRecords) {
                if (bill == null || !StringUtils.hasText(bill.getBillNo())) {
                    continue;
                }
                PdaReceiveScanSession session = sessionMap.get(bill.getBillNo().trim());
                if (session != null && "COMPLETED".equals(session.getStatus())) {
                    continue;
                }
                if (bill.getLines() != null && !bill.getLines().isEmpty()) {
                    bill.setLines(null);
                }
                merged.put(bill.getBillNo(), toListItem(bill, session, billType));
            }

            List<PdaReceiveScanSession> openSessions = sessionMapper.selectList(new LambdaQueryWrapper<PdaReceiveScanSession>()
                    .eq(PdaReceiveScanSession::getBillType, billType.getCode())
                    .eq(PdaReceiveScanSession::getDirection, billType.getDirection().name())
                    .in(PdaReceiveScanSession::getStatus, List.of("SCANNING", "PARTIAL_SUBMITTED"))
                    .orderByDesc(PdaReceiveScanSession::getUpdateTime));
            for (PdaReceiveScanSession session : openSessions) {
                if (!StringUtils.hasText(session.getBillNo()) || isMockReceiveBillNo(session.getBillNo())) {
                    continue;
                }
                if (merged.containsKey(session.getBillNo())) {
                    continue;
                }
                if (!provider.isErpEnabled()) {
                    merged.put(session.getBillNo(), toListItem(mockFromSession(session), session, billType));
                }
            }

            visible = merged.values().stream()
                    .filter(v -> !"COMPLETED".equals(v.getScanStatus()))
                    .sorted(noticeNewestFirst())
                    .collect(Collectors.toList());
            enrichLatestErpInfo(visible);
            if (StringUtils.hasText(keyword)) {
                String kw = keyword.trim().toLowerCase();
                visible = visible.stream()
                        .filter(v -> contains(v.getBillNo(), kw)
                                || contains(v.getSupplierName(), kw)
                                || contains(v.getSupplierCode(), kw))
                        .collect(Collectors.toList());
            }
            if (pdaProperties.getShortCacheTtlSeconds() > 0) {
                pdaShortCache.put(visibleCacheKey, visible,
                        Duration.ofSeconds(Math.max(60, pdaProperties.getShortCacheTtlSeconds())));
            }
        }

        long total = visible.size();
        int from = (int) Math.max(0, (page - 1) * pageSize);
        int to = (int) Math.min(visible.size(), from + (int) pageSize);
        List<ReceiveNoticeListItemVo> slice = from < visible.size() ? visible.subList(from, to) : List.of();
        return PageResult.of(slice, total, page, pageSize);
    }

    public String resolveBillNoFromBarcode(String barcode) {
        return resolveBillNoFromBarcode(NoticeBillType.PURCHASE_RECEIVE, barcode);
    }

    public String resolveBillNoFromBarcode(NoticeBillType billType, String barcode) {
        return noticeBillProviderRegistry.require(billType).parseBillNo(barcode);
    }

    public ReceiveNoticeDetailVo getDetail(String billNo) {
        return getDetail(NoticeBillType.PURCHASE_RECEIVE, billNo);
    }

    public ReceiveNoticeDetailVo getDetail(NoticeBillType billType, String billNo) {
        return getDetail(billType, billNo, false);
    }

    public ReceiveNoticeDetailVo getDetail(NoticeBillType billType, String billNo, boolean forceRefresh) {
        NoticeBillProvider provider = noticeBillProviderRegistry.require(billType);
        String no = billNo != null ? billNo.trim() : "";
        PdaReceiveScanSession existingSession = loadSession(billType, no);
        List<PdaReceiveScanLine> existingLines = loadLines(billType, no);
        int preferSec = pdaProperties.getDetailLocalPreferSeconds();
        if (!forceRefresh
                && preferSec > 0
                && existingSession != null
                && existingLines != null
                && !existingLines.isEmpty()
                && existingSession.getUpdateTime() != null
                && existingSession.getUpdateTime().isAfter(LocalDateTime.now().minusSeconds(preferSec))) {
            return buildDetail(mockFromSession(existingSession), existingSession, existingLines, billType);
        }

        String billCacheKey = "bill:" + billType.getCode() + ":" + no.toUpperCase();
        KingdeeReceiveBillVo bill = null;
        if (!forceRefresh && pdaProperties.getShortCacheTtlSeconds() > 0) {
            bill = pdaShortCache.get(billCacheKey, KingdeeReceiveBillVo.class);
        }
        if (bill == null) {
            bill = provider.getBill(no);
            if (bill != null && pdaProperties.getShortCacheTtlSeconds() > 0) {
                pdaShortCache.put(billCacheKey, bill, Duration.ofSeconds(pdaProperties.getShortCacheTtlSeconds()));
            }
        }
        if (bill == null) {
            if (!provider.isErpEnabled()) {
                if (existingSession != null) {
                    return buildDetail(mockFromSession(existingSession), existingSession, existingLines, billType);
                }
            }
            throw new BusinessException(ErrorCode.NOT_FOUND, billType.getLabel() + "不存在或已完结: " + no);
        }
        PdaReceiveScanSession session = ensureSession(billType, bill);
        syncLinesFromKingdee(session, bill);
        return buildDetail(bill, session, loadLines(billType, no), billType);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo scanLine(String billNo, ReceiveNoticeScanRequest req) {
        return scanLine(NoticeBillType.PURCHASE_RECEIVE, billNo, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo scanLine(NoticeBillType billType, String billNo, ReceiveNoticeScanRequest req) {
        List<PdaReceiveScanLine> billLines = loadLines(billType, billNo);
        PdaReceiveScanSession existing = loadSession(billType, billNo);
        if (existing == null || billLines == null || billLines.isEmpty()
                || !pdaProperties.isScanSkipKingdeeRefresh()) {
            getDetail(billType, billNo, false);
            billLines = loadLines(billType, billNo);
        }
        String raw = req.getBarcodeContent() != null ? req.getBarcodeContent().trim() : "";
        BarcodeRecognizeResult recognized = barcodeRecognizeService.recognize(raw);
        BillMaterialMatch match = resolveMaterialOnBill(raw, billLines, recognized);
        BigDecimal scanQty = barcodeRecognizeService.resolveScanQuantity(raw, recognized);

        PdaReceiveScanLine line = pickScanLine(billType, billNo, match.materialCode(), scanQty);
        if (line == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "收料单明细同步异常，未找到物料行: " + match.materialCode() + "，请返回列表重新进入",
                    "BILL_LINE_NOT_SYNCED");
        }
        BigDecimal submitted = nullSafe(line.getSubmittedQty());
        BigDecimal remain = nullSafe(line.getPlanQty()).subtract(submitted);
        if (remain.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该物料已收满", "LINE_ALREADY_FULL");
        }
        String resolvedBatch = resolveLineBatchNo(
                match.batchNo(), raw, null);
        if (StringUtils.hasText(resolvedBatch)) {
            line.setBatchNo(resolvedBatch);
        }
        line.setChecked(1);
        BigDecimal currentPending = nullSafe(line.getScannedQty()).subtract(submitted);
        if (currentPending.compareTo(BigDecimal.ZERO) < 0) {
            currentPending = BigDecimal.ZERO;
        }
        BigDecimal nextPending = currentPending.add(scanQty);
        if (nextPending.compareTo(remain) > 0) {
            nextPending = remain;
        }
        line.setScannedQty(submitted.add(nextPending));
        line.setScannedBarcode(req.getBarcodeContent());
        line.setLastScanTime(LocalDateTime.now());
        line.setUpdateTime(LocalDateTime.now());
        lineMapper.updateById(line);

        PdaReceiveScanSession session = requireSession(billType, billNo);
        refreshSessionCounters(session);
        session.setLastScanTime(LocalDateTime.now());
        session.setDeviceNo(req.getDeviceNo());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        ReceiveNoticeLineVo vo = toLineVo(line);
        vo.setScannedBarcodeQty(scanQty);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(String billNo, ReceiveNoticeSubmitRequest req) {
        return submit(NoticeBillType.PURCHASE_RECEIVE, billNo, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(NoticeBillType billType, String billNo, ReceiveNoticeSubmitRequest req) {
        LoginUser user = SecurityUtils.currentUser();
        KingdeeReceiveBillVo kdBill = refreshKingdeeContextBeforeSubmit(billType, billNo, req);
        PdaReceiveScanSession session = requireSession(billType, billNo);
        List<PdaReceiveScanLine> lines = loadLines(billType, billNo).stream()
                .filter(l -> l.getChecked() != null && l.getChecked() == 1)
                .filter(l -> nullSafe(l.getScannedQty()).compareTo(nullSafe(l.getSubmittedQty())) > 0)
                .collect(Collectors.toList());
        if (lines.isEmpty()) {
            String action = billType.getDirection().isInbound() ? "入库" : "出库";
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先扫描并勾选待" + action + "物料");
        }

        boolean inbound = billType.getDirection().isInbound();
        if (inbound) {
            validateInboundQtyAgainstKingdee(lines, kdBill);
        }

        String supplierCode = firstNonBlank(req.getSupplierCode(), session.getSupplierCode(), kdBill.getSupplierCode());
        String supplierName = firstNonBlank(req.getSupplierName(), session.getSupplierName(), kdBill.getSupplierName());
        if (inbound && !StringUtils.hasText(supplierCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "缺少供应商编码，请返回列表重新进入收料单",
                    "MISSING_SUPPLIER");
        }

        String defaultWarehouse = StringUtils.hasText(req.getWarehouseCode())
                ? req.getWarehouseCode() : session.getWarehouseCode();
        boolean autoAllocate = req.getAutoAllocateLocation() == null || req.getAutoAllocateLocation();
        String batchNo = generateSubmitBatchNo();
        BigDecimal totalQty = BigDecimal.ZERO;
        List<String> recordNos = new ArrayList<>();
        List<KingdeePickMtrlRequest.Line> pickMtrlLines = new ArrayList<>();
        List<KingdeeSubPickMtrlRequest.Line> subPickMtrlLines = new ArrayList<>();
        List<KingdeeReturnMtrlRequest.Line> returnMtrlLines = new ArrayList<>();
        List<KingdeeSubReturnMtrlRequest.Line> subReturnMtrlLines = new ArrayList<>();

        PdaReceiveSubmitBatch batch = new PdaReceiveSubmitBatch();
        batch.setBatchNo(batchNo);
        batch.setBillType(billType.getCode());
        batch.setDirection(billType.getDirection().name());
        batch.setBillNo(billNo);
        batch.setSupplierCode(supplierCode);
        batch.setSupplierName(supplierName);
        batch.setLineCount(lines.size());
        batch.setErpSyncStatus("PENDING");
        batch.setOperatorId(String.valueOf(user.getUserId()));
        batch.setOperatorName(user.getRealName() != null ? user.getRealName() : user.getUsername());
        batch.setDeviceNo(req.getDeviceNo());
        batch.setSubmitTime(LocalDateTime.now());
        submitBatchMapper.insert(batch);

        boolean autoAssignWarehouse = req.getAutoAssignWarehouse() == null || req.getAutoAssignWarehouse();
        for (PdaReceiveScanLine line : lines) {
            BigDecimal qty = nullSafe(line.getScannedQty()).subtract(nullSafe(line.getSubmittedQty()));
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            KingdeeReceiveBillLineVo kdLine = findKdLine(kdBill, line);
            String batchNoMat = resolveLineBatchNo(
                    line.getBatchNo(),
                    line.getScannedBarcode(),
                    kdLine != null ? kdLine.getBatchNo() : null);
            if (!batchNoMat.equals(line.getBatchNo())) {
                line.setBatchNo(batchNoMat);
                line.setUpdateTime(LocalDateTime.now());
                lineMapper.updateById(line);
            }
            String warehouse;
            String locationCode;
            String erpStockCode;
            if (inbound) {
                if (autoAssignWarehouse) {
                    warehouse = resolveLineWarehouse(line, kdLine, req);
                    if (!StringUtils.hasText(warehouse)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "第" + line.getLineNo() + "行缺少金蝶仓库编码，请返回重新加载收料单",
                                "MISSING_ERP_STOCK");
                    }
                    erpStockCode = erpWarehouseResolver.resolve(null, warehouse);
                } else {
                    warehouse = resolveManualInboundWarehouse(req);
                    if (!StringUtils.hasText(warehouse)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "请选择入库仓库",
                                "MISSING_WAREHOUSE");
                    }
                    erpStockCode = erpWarehouseResolver.resolve(
                            req.getWarehouseCode(), firstNonBlank(req.getErpWarehouseCode(), warehouse));
                }
                locationCode = "";
            } else {
                warehouse = defaultWarehouse;
                locationCode = resolveLocation(billType, warehouse, line, batchNoMat, autoAllocate, req.getLocationCode());
                erpStockCode = resolveSubmitErpStockCode(warehouse, line, kdLine, req.getErpWarehouseCode());
            }
            String unitCode = firstNonBlank(line.getUnitCode(), kdLine != null ? kdLine.getUnitCode() : null);
            String recordNo = generateRecordNo();

            if (inbound) {
                PdaInboundRecord record = new PdaInboundRecord();
                record.setRecordNo(recordNo);
                record.setMaterialCode(line.getMaterialCode());
                record.setMaterialName(line.getMaterialName());
                record.setSpecification(line.getSpecification());
                record.setUnitCode(unitCode);
                record.setWarehouseCode(warehouse);
                record.setLocationCode(locationCode);
                record.setBatchNo(batchNoMat);
                record.setQuantity(qty);
                record.setBarcodeContent(line.getScannedBarcode());
                record.setOperatorId(batch.getOperatorId());
                record.setOperatorName(batch.getOperatorName());
                record.setDeviceNo(req.getDeviceNo());
                record.setStatus("SUBMITTED");
                record.setErpSyncStatus("PENDING");
                record.setErpRetryCount(0);
                record.setSourceType(billType.getSourceOrderType());
                record.setSourceBillNo(billNo);
                record.setSourceLineNo(line.getLineNo());
                record.setSubmitBatchNo(batchNo);
                record.setSupplierCode(supplierCode);
                record.setErpStockCode(erpStockCode);
                record.setSourceBillId(kdBill.getBillId());
                record.setSourceEntryId(kdLine != null ? kdLine.getEntryId() : null);
                if (kdLine != null) {
                    record.setSourceRemainInStockBaseQtyOld(kdLine.getRemainInStockBaseQty());
                    record.setSourceBaseUnitQtyOld(kdLine.getBaseUnitQty());
                }
                record.setRemark(req.getRemark());
                record.setCreateTime(LocalDateTime.now());
                inboundRecordMapper.insert(record);

                InventoryChangeResult changeResult = inventoryService.increaseWithTransaction(InventoryChangeCommand.builder()
                        .transactionType(billType.getTransactionType())
                        .warehouseCode(warehouse)
                        .locationCode(locationCode)
                        .materialCode(line.getMaterialCode())
                        .batchNo(batchNoMat)
                        .quantity(qty)
                        .sourceOrderType(billType.getSourceOrderType())
                        .sourceOrderNo(billNo)
                        .sourceOrderLine(line.getLineNo())
                        .operatorId(record.getOperatorId())
                        .operatorName(record.getOperatorName())
                        .deviceNo(record.getDeviceNo())
                        .remark(line.getScannedBarcode())
                        .build());
                barcodeTraceLinkService.linkPdaInbound(
                        line.getScannedBarcode(), recordNo, changeResult.getTransactionNo(),
                        line.getMaterialCode(), batchNoMat, null);
                if (billType == NoticeBillType.PRODUCTION_RETURN) {
                    String kdWarehouse = firstNonBlank(erpStockCode, warehouse);
                    if (!StringUtils.hasText(kdWarehouse)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "第" + line.getLineNo() + "行缺少金蝶仓库编码，无法同步生产退料单",
                                "MISSING_ERP_STOCK");
                    }
                    returnMtrlLines.add(KingdeeReturnMtrlRequest.Line.builder()
                            .materialCode(line.getMaterialCode())
                            .materialName(line.getMaterialName())
                            .unitCode(unitCode)
                            .warehouseCode(kdWarehouse)
                            .locationCode(locationCode)
                            .batchNo(batchNoMat)
                            .quantity(qty)
                            .sourceLineNo(line.getLineNo())
                            .entryNote(line.getScannedBarcode())
                            .parentMaterialCode(firstNonBlank(
                                    kdLine != null ? kdLine.getParentMaterialCode() : null,
                                    kdBill.getParentMaterialCode()))
                            .moBillNo(firstNonBlank(
                                    kdLine != null ? kdLine.getMoBillNo() : null,
                                    kdBill.getMoBillNo()))
                            .moId(kdLine != null ? kdLine.getMoId() : null)
                            .moEntryId(kdLine != null ? kdLine.getMoEntryId() : null)
                            .moEntrySeq(kdLine != null ? kdLine.getMoEntrySeq() : null)
                            .ppBomBillNo(kdLine != null ? kdLine.getPpBomBillNo() : null)
                            .ppBomEntryId(kdLine != null ? kdLine.getPpBomEntryId() : null)
                            .pickEntryId(kdLine != null ? kdLine.getEntryId() : null)
                            .pickBillId(kdBill.getBillId())
                            .build());
                } else if (billType == NoticeBillType.OUTSOURCE_RETURN) {
                    String kdWarehouse = firstNonBlank(erpStockCode, warehouse);
                    if (!StringUtils.hasText(kdWarehouse)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "第" + line.getLineNo() + "行缺少金蝶仓库编码，无法同步委外退料单",
                                "MISSING_ERP_STOCK");
                    }
                    subReturnMtrlLines.add(KingdeeSubReturnMtrlRequest.Line.builder()
                            .materialCode(line.getMaterialCode())
                            .materialName(line.getMaterialName())
                            .unitCode(unitCode)
                            .warehouseCode(kdWarehouse)
                            .locationCode(locationCode)
                            .batchNo(batchNoMat)
                            .quantity(qty)
                            .sourceLineNo(line.getLineNo())
                            .entryNote(line.getScannedBarcode())
                            .parentMaterialCode(firstNonBlank(
                                    kdLine != null ? kdLine.getParentMaterialCode() : null,
                                    kdBill.getParentMaterialCode()))
                            .subReqBillNo(firstNonBlank(
                                    kdLine != null ? kdLine.getSubReqBillNo() : null,
                                    kdBill.getMoBillNo()))
                            .subReqId(kdLine != null ? kdLine.getSubReqId() : null)
                            .subReqEntryId(kdLine != null ? kdLine.getSubReqEntryId() : null)
                            .subReqEntrySeq(kdLine != null ? kdLine.getSubReqEntrySeq() : null)
                            .ppBomBillNo(kdLine != null ? kdLine.getPpBomBillNo() : null)
                            .ppBomEntryId(kdLine != null ? kdLine.getPpBomEntryId() : null)
                            .pickEntryId(kdLine != null ? kdLine.getEntryId() : null)
                            .pickBillId(kdBill.getBillId())
                            .build());
                }
            } else {
                inventoryService.decreaseWithTransaction(InventoryChangeCommand.builder()
                        .transactionType(billType.getTransactionType())
                        .warehouseCode(warehouse)
                        .locationCode(locationCode)
                        .materialCode(line.getMaterialCode())
                        .batchNo(batchNoMat)
                        .quantity(qty)
                        .sourceOrderType(billType.getSourceOrderType())
                        .sourceOrderNo(billNo)
                        .sourceOrderLine(line.getLineNo())
                        .operatorId(batch.getOperatorId())
                        .operatorName(batch.getOperatorName())
                        .deviceNo(req.getDeviceNo())
                        .remark(line.getScannedBarcode())
                        .build());
                if (billType == NoticeBillType.PRODUCTION_ISSUE) {
                    String kdWarehouse = firstNonBlank(erpStockCode, warehouse);
                    if (!StringUtils.hasText(kdWarehouse)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "第" + line.getLineNo() + "行缺少金蝶仓库编码，无法同步生产领料单",
                                "MISSING_ERP_STOCK");
                    }
                    pickMtrlLines.add(KingdeePickMtrlRequest.Line.builder()
                            .materialCode(line.getMaterialCode())
                            .materialName(line.getMaterialName())
                            .unitCode(unitCode)
                            .warehouseCode(kdWarehouse)
                            .locationCode(locationCode)
                            .batchNo(batchNoMat)
                            .quantity(qty)
                            .sourceLineNo(line.getLineNo())
                            .entryNote(line.getScannedBarcode())
                            .parentMaterialCode(firstNonBlank(
                                    kdLine != null ? kdLine.getParentMaterialCode() : null,
                                    kdBill.getParentMaterialCode()))
                            .moBillNo(firstNonBlank(
                                    kdLine != null ? kdLine.getMoBillNo() : null,
                                    kdBill.getMoBillNo()))
                            .moId(kdLine != null ? kdLine.getMoId() : null)
                            .moEntryId(kdLine != null ? kdLine.getMoEntryId() : null)
                            .moEntrySeq(kdLine != null ? kdLine.getMoEntrySeq() : null)
                            .ppBomBillNo(firstNonBlank(
                                    kdLine != null ? kdLine.getPpBomBillNo() : null,
                                    billNo))
                            .ppBomEntryId(firstNonNull(
                                    kdLine != null ? kdLine.getPpBomEntryId() : null,
                                    kdLine != null ? kdLine.getEntryId() : null))
                            .ppBomBillId(kdBill.getBillId())
                            .build());
                } else if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
                    String kdWarehouse = firstNonBlank(erpStockCode, warehouse);
                    if (!StringUtils.hasText(kdWarehouse)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "第" + line.getLineNo() + "行缺少金蝶仓库编码，无法同步委外领料单",
                                "MISSING_ERP_STOCK");
                    }
                    subPickMtrlLines.add(KingdeeSubPickMtrlRequest.Line.builder()
                            .materialCode(line.getMaterialCode())
                            .materialName(line.getMaterialName())
                            .unitCode(unitCode)
                            .warehouseCode(kdWarehouse)
                            .locationCode(locationCode)
                            .batchNo(batchNoMat)
                            .quantity(qty)
                            .sourceLineNo(line.getLineNo())
                            .entryNote(line.getScannedBarcode())
                            .parentMaterialCode(firstNonBlank(
                                    kdLine != null ? kdLine.getParentMaterialCode() : null,
                                    kdBill.getParentMaterialCode()))
                            .subReqBillNo(firstNonBlank(
                                    kdLine != null ? kdLine.getSubReqBillNo() : null,
                                    kdBill.getMoBillNo()))
                            .subReqId(kdLine != null ? kdLine.getSubReqId() : null)
                            .subReqEntryId(kdLine != null ? kdLine.getSubReqEntryId() : null)
                            .subReqEntrySeq(kdLine != null ? kdLine.getSubReqEntrySeq() : null)
                            .ppBomBillNo(firstNonBlank(
                                    kdLine != null ? kdLine.getPpBomBillNo() : null,
                                    billNo))
                            .ppBomEntryId(firstNonNull(
                                    kdLine != null ? kdLine.getPpBomEntryId() : null,
                                    kdLine != null ? kdLine.getEntryId() : null))
                            .ppBomBillId(kdBill.getBillId())
                            .build());
                }
            }

            line.setSubmittedQty(nullSafe(line.getSubmittedQty()).add(qty));
            line.setUpdateTime(LocalDateTime.now());
            lineMapper.updateById(line);
            totalQty = totalQty.add(qty);
            recordNos.add(recordNo);
        }

        batch.setTotalQty(totalQty);
        int outboundLines = !pickMtrlLines.isEmpty()
                ? pickMtrlLines.size()
                : (!subPickMtrlLines.isEmpty() ? subPickMtrlLines.size() : recordNos.size());
        batch.setLineCount(inbound ? recordNos.size() : outboundLines);
        submitBatchMapper.updateById(batch);
        refreshSessionCounters(session);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        Map<String, Object> result = new HashMap<>();
        result.put("batchNo", batchNo);
        result.put("billNo", billNo);
        result.put("billType", billType.getCode());
        result.put("direction", billType.getDirection().name());
        result.put("recordNos", recordNos);
        result.put("lineCount", batch.getLineCount());
        result.put("totalQty", totalQty);
        result.put("scanStatus", session.getStatus());
        result.put("erpSyncStatus", "PENDING");
        result.put("message", billType == NoticeBillType.PURCHASE_RECEIVE
                ? "已提交至WMS，正在同步金蝶"
                : (billType == NoticeBillType.PRODUCTION_RETURN
                ? "已入库增加库存，正在同步金蝶生产退料单"
                : (billType == NoticeBillType.OUTSOURCE_RETURN
                ? "已入库增加库存，正在同步金蝶委外退料单"
                : (billType == NoticeBillType.PRODUCTION_ISSUE
                ? "已出库扣减库存，正在同步金蝶生产领料单"
                : (billType == NoticeBillType.OUTSOURCE_ISSUE
                ? "已出库扣减库存，正在同步金蝶委外领料单"
                : (inbound ? "已提交至WMS" : "已出库扣减库存"))))));

        boolean needErp = (billType == NoticeBillType.PURCHASE_RECEIVE && !recordNos.isEmpty())
                || (billType == NoticeBillType.PRODUCTION_RETURN && !returnMtrlLines.isEmpty())
                || (billType == NoticeBillType.OUTSOURCE_RETURN && !subReturnMtrlLines.isEmpty())
                || (billType == NoticeBillType.PRODUCTION_ISSUE && !pickMtrlLines.isEmpty())
                || (billType == NoticeBillType.OUTSOURCE_ISSUE && !subPickMtrlLines.isEmpty());
        if (needErp) {
            evictBillCaches(billType, billNo);
            dispatchErpSync(billType, batchNo, result,
                    recordNos, returnMtrlLines, subReturnMtrlLines, pickMtrlLines, subPickMtrlLines);
        }
        return result;
    }

    private void dispatchErpSync(NoticeBillType billType, String batchNo, Map<String, Object> result,
                                 List<String> recordNos,
                                 List<KingdeeReturnMtrlRequest.Line> returnMtrlLines,
                                 List<KingdeeSubReturnMtrlRequest.Line> subReturnMtrlLines,
                                 List<KingdeePickMtrlRequest.Line> pickMtrlLines,
                                 List<KingdeeSubPickMtrlRequest.Line> subPickMtrlLines) {
        boolean async = pdaProperties.isAsyncErpSync();
        if (async) {
            List<KingdeeReturnMtrlRequest.Line> returnCopy = new ArrayList<>(returnMtrlLines);
            List<KingdeeSubReturnMtrlRequest.Line> subReturnCopy = new ArrayList<>(subReturnMtrlLines);
            List<KingdeePickMtrlRequest.Line> pickCopy = new ArrayList<>(pickMtrlLines);
            List<KingdeeSubPickMtrlRequest.Line> subCopy = new ArrayList<>(subPickMtrlLines);
            Runnable task = () -> {
                switch (billType) {
                    case PURCHASE_RECEIVE -> erpSyncAsyncService.syncPurchaseInStock(batchNo);
                    case PRODUCTION_RETURN -> erpSyncAsyncService.syncReturnMtrl(batchNo, returnCopy);
                    case OUTSOURCE_RETURN -> erpSyncAsyncService.syncSubReturnMtrl(batchNo, subReturnCopy);
                    case PRODUCTION_ISSUE -> erpSyncAsyncService.syncPickMtrl(batchNo, pickCopy);
                    case OUTSOURCE_ISSUE -> erpSyncAsyncService.syncSubPickMtrl(batchNo, subCopy);
                    default -> {
                    }
                }
            };
            scheduleAfterCommit(task);
            result.put("erpSyncAsync", true);
            result.put("message", String.valueOf(result.get("message")) + "（后台同步）");
            return;
        }
        if (billType == NoticeBillType.PURCHASE_RECEIVE && !recordNos.isEmpty()) {
            Map<String, Object> syncResult = submitBatchService.syncBatchToErp(batchNo);
            applyErpSyncResult(result, batchNo, syncResult, "采购入库单");
        } else if (billType == NoticeBillType.PRODUCTION_RETURN && !returnMtrlLines.isEmpty()) {
            Map<String, Object> syncResult = submitBatchService.syncReturnMtrlBatch(batchNo, returnMtrlLines);
            applyErpSyncResult(result, batchNo, syncResult, "生产退料单");
        } else if (billType == NoticeBillType.OUTSOURCE_RETURN && !subReturnMtrlLines.isEmpty()) {
            Map<String, Object> syncResult = submitBatchService.syncSubReturnMtrlBatch(batchNo, subReturnMtrlLines);
            applyErpSyncResult(result, batchNo, syncResult, "委外退料单");
        } else if (billType == NoticeBillType.PRODUCTION_ISSUE && !pickMtrlLines.isEmpty()) {
            Map<String, Object> syncResult = submitBatchService.syncPickMtrlBatch(batchNo, pickMtrlLines);
            applyErpSyncResult(result, batchNo, syncResult, "生产领料单");
        } else if (billType == NoticeBillType.OUTSOURCE_ISSUE && !subPickMtrlLines.isEmpty()) {
            Map<String, Object> syncResult = submitBatchService.syncSubPickMtrlBatch(batchNo, subPickMtrlLines);
            applyErpSyncResult(result, batchNo, syncResult, "委外领料单");
        }
    }

    private void scheduleAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void evictBillCaches(NoticeBillType billType, String billNo) {
        if (billType == null || !StringUtils.hasText(billNo)) {
            return;
        }
        String no = billNo.trim().toUpperCase();
        pdaShortCache.evict("bill:" + billType.getCode() + ":" + no);
        pdaShortCache.evictByPrefix("list:" + billType.getCode() + ":");
    }

    private void applyErpSyncResult(Map<String, Object> result, String batchNo,
                                    Map<String, Object> syncResult, String billLabel) {
        String erpSyncStatus = String.valueOf(syncResult.getOrDefault("erpSyncStatus", ""));
        result.put("erpSyncStatus", erpSyncStatus);
        result.put("erpBillNo", syncResult.get("erpBillNo"));
        result.put("erpSyncMessage", syncResult.get("erpSyncMessage"));
        if (!"SUCCESS".equals(erpSyncStatus)) {
            String erpMsg = syncResult.get("erpSyncMessage") != null
                    ? String.valueOf(syncResult.get("erpSyncMessage"))
                    : "金蝶同步失败";
            throw new BusinessException(ErrorCode.CONFLICT,
                    KingdeeResponseMessageFormatter.format(erpMsg),
                    "ERP_SYNC_FAILED",
                    Map.of("batchNo", batchNo, "erpSyncStatus", erpSyncStatus));
        }
        String erpBillNo = syncResult.get("erpBillNo") != null
                ? String.valueOf(syncResult.get("erpBillNo")) : null;
        if (StringUtils.hasText(erpBillNo)) {
            result.put("message", "已提交并同步金蝶，" + billLabel + " " + erpBillNo);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo toggleLine(String billNo, Integer lineNo, boolean checked) {
        return toggleLine(NoticeBillType.PURCHASE_RECEIVE, billNo, lineNo, checked);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo toggleLine(NoticeBillType billType, String billNo, Integer lineNo, boolean checked) {
        PdaReceiveScanLine line = lineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                .eq(PdaReceiveScanLine::getBillType, billType.getCode())
                .eq(PdaReceiveScanLine::getDirection, billType.getDirection().name())
                .eq(PdaReceiveScanLine::getBillNo, billNo)
                .eq(PdaReceiveScanLine::getLineNo, lineNo));
        if (line == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "明细行不存在");
        }
        line.setChecked(checked ? 1 : 0);
        line.setUpdateTime(LocalDateTime.now());
        lineMapper.updateById(line);
        PdaReceiveScanSession session = requireSession(billType, billNo);
        refreshSessionCounters(session);
        sessionMapper.updateById(session);
        return toLineVo(line);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo updateLineQty(String billNo, Integer lineNo, ReceiveNoticeQtyRequest req) {
        return updateLineQty(NoticeBillType.PURCHASE_RECEIVE, billNo, lineNo, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo updateLineQty(NoticeBillType billType, String billNo, Integer lineNo, ReceiveNoticeQtyRequest req) {
        PdaReceiveScanLine line = lineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                .eq(PdaReceiveScanLine::getBillType, billType.getCode())
                .eq(PdaReceiveScanLine::getDirection, billType.getDirection().name())
                .eq(PdaReceiveScanLine::getBillNo, billNo)
                .eq(PdaReceiveScanLine::getLineNo, lineNo));
        if (line == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "明细行不存在");
        }
        BigDecimal pending = req.getQty();
        if (pending == null || pending.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "领取数量不能为负");
        }
        BigDecimal submitted = nullSafe(line.getSubmittedQty());
        BigDecimal plan = nullSafe(line.getPlanQty());
        BigDecimal remain = plan.subtract(submitted);
        if (remain.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该物料已领满");
        }
        if (pending.compareTo(remain) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "领取数量不能超过剩余可领 " + remain.stripTrailingZeros().toPlainString());
        }
        line.setScannedQty(submitted.add(pending));
        line.setChecked(1);
        line.setUpdateTime(LocalDateTime.now());
        lineMapper.updateById(line);
        PdaReceiveScanSession session = requireSession(billType, billNo);
        refreshSessionCounters(session);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return toLineVo(line);
    }

    private PdaReceiveScanSession ensureSession(NoticeBillType billType, KingdeeReceiveBillVo bill) {
        PdaReceiveScanSession session = loadSession(billType, bill.getBillNo());
        LoginUser user = SecurityUtils.currentUser();
        if (session == null) {
            session = new PdaReceiveScanSession();
            session.setBillType(billType.getCode());
            session.setDirection(billType.getDirection().name());
            session.setBillNo(bill.getBillNo());
            session.setSupplierCode(bill.getSupplierCode());
            session.setSupplierName(bill.getSupplierName());
            session.setBillDate(bill.getBillDate());
            session.setWarehouseCode(StringUtils.hasText(bill.getWarehouseCode()) ? bill.getWarehouseCode() : "WH01");
            session.setStatus("SCANNING");
            session.setTotalLines(bill.getLines() != null ? bill.getLines().size() : 0);
            session.setCheckedLines(0);
            session.setSubmittedLines(0);
            session.setOperatorId(String.valueOf(user.getUserId()));
            session.setOperatorName(user.getRealName() != null ? user.getRealName() : user.getUsername());
            session.setCreateTime(LocalDateTime.now());
            sessionMapper.insert(session);
        } else {
            boolean changed = false;
            if (StringUtils.hasText(bill.getSupplierCode())
                    && !bill.getSupplierCode().equals(session.getSupplierCode())) {
                session.setSupplierCode(bill.getSupplierCode());
                changed = true;
            }
            if (StringUtils.hasText(bill.getSupplierName())
                    && !bill.getSupplierName().equals(session.getSupplierName())) {
                session.setSupplierName(bill.getSupplierName());
                changed = true;
            }
            if (bill.getBillDate() != null && !bill.getBillDate().equals(session.getBillDate())) {
                session.setBillDate(bill.getBillDate());
                changed = true;
            }
            if (changed) {
                session.setUpdateTime(LocalDateTime.now());
                sessionMapper.updateById(session);
            }
        }
        return session;
    }

    private void syncLinesFromKingdee(PdaReceiveScanSession session, KingdeeReceiveBillVo bill) {
        if (bill.getLines() == null) {
            return;
        }
        for (KingdeeReceiveBillLineVo kdLine : bill.getLines()) {
            PdaReceiveScanLine existing = lineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                    .eq(PdaReceiveScanLine::getBillType, session.getBillType())
                    .eq(PdaReceiveScanLine::getDirection, session.getDirection())
                    .eq(PdaReceiveScanLine::getBillNo, session.getBillNo())
                    .eq(PdaReceiveScanLine::getLineNo, kdLine.getLineNo()));
            if (existing == null) {
                PdaReceiveScanLine line = new PdaReceiveScanLine();
                line.setSessionId(session.getId());
                line.setBillType(session.getBillType());
                line.setDirection(session.getDirection());
                line.setBillNo(session.getBillNo());
                line.setLineNo(kdLine.getLineNo());
                line.setMaterialCode(kdLine.getMaterialCode());
                line.setMaterialName(resolveMaterialDisplayName(kdLine));
                line.setSpecification(kdLine.getSpecification());
                line.setBatchNo(BatchNoNormalizer.normalize(kdLine.getBatchNo()));
                line.setPlanQty(resolvePlanQty(kdLine));
                line.setChecked(0);
                line.setScannedQty(BigDecimal.ZERO);
                line.setSubmittedQty(BigDecimal.ZERO);
                line.setUnitCode(kdLine.getUnitCode());
                line.setErpStockCode(kdLine.getStockWarehouseCode());
                line.setCreateTime(LocalDateTime.now());
                lineMapper.insert(line);
            } else {
                existing.setMaterialName(resolveMaterialDisplayName(kdLine));
                existing.setSpecification(kdLine.getSpecification());
                String kdBatch = BatchNoNormalizer.normalize(kdLine.getBatchNo());
                if (StringUtils.hasText(kdBatch)) {
                    existing.setBatchNo(kdBatch);
                } else if (BatchNoNormalizer.isMalformed(existing.getBatchNo())) {
                    existing.setBatchNo(null);
                }
                existing.setPlanQty(resolvePlanQty(kdLine));
                existing.setUnitCode(kdLine.getUnitCode());
                if (StringUtils.hasText(kdLine.getStockWarehouseCode())) {
                    existing.setErpStockCode(kdLine.getStockWarehouseCode());
                }
                reconcileSubmittedQtyFromKingdee(existing, kdLine);
                existing.setUpdateTime(LocalDateTime.now());
                lineMapper.updateById(existing);
            }
        }
        session.setTotalLines(bill.getLines().size());
        refreshSessionCounters(session);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private void reconcileSubmittedQtyFromKingdee(PdaReceiveScanLine line, KingdeeReceiveBillLineVo kdLine) {
        if (line == null || kdLine == null || kdLine.getInStockJoinBaseQty() == null) {
            return;
        }
        BigDecimal erpSubmitted = kdLine.getInStockJoinBaseQty();
        if (erpSubmitted.compareTo(BigDecimal.ZERO) < 0) {
            erpSubmitted = BigDecimal.ZERO;
        }
        BigDecimal wmsSubmitted = nullSafe(line.getSubmittedQty());
        if (erpSubmitted.compareTo(wmsSubmitted) == 0) {
            return;
        }
        log.info("Reconcile submittedQty from Kingdee receive bill: bill={} line={} material={} wms={} erp={}",
                line.getBillNo(), line.getLineNo(), line.getMaterialCode(),
                wmsSubmitted.stripTrailingZeros().toPlainString(),
                erpSubmitted.stripTrailingZeros().toPlainString());
        line.setSubmittedQty(erpSubmitted);
        // 金蝶已入库量减少时，同步压低本次待提交量，避免显示可领数量异常
        if (nullSafe(line.getScannedQty()).compareTo(erpSubmitted) < 0) {
            line.setScannedQty(erpSubmitted);
        }
        if (erpSubmitted.compareTo(BigDecimal.ZERO) <= 0) {
            line.setChecked(0);
        }
    }

    private void refreshSessionCounters(PdaReceiveScanSession session) {
        List<PdaReceiveScanLine> lines = loadLines(
                NoticeBillType.fromCode(session.getBillType()), session.getBillNo());
        int checked = (int) lines.stream().filter(l -> l.getChecked() != null && l.getChecked() == 1).count();
        int submitted = (int) lines.stream()
                .filter(l -> nullSafe(l.getSubmittedQty()).compareTo(nullSafe(l.getPlanQty())) >= 0
                        && nullSafe(l.getPlanQty()).compareTo(BigDecimal.ZERO) > 0)
                .count();
        session.setCheckedLines(checked);
        session.setSubmittedLines(submitted);
        session.setTotalLines(lines.size());
        if (submitted >= lines.size() && !lines.isEmpty()) {
            session.setStatus("COMPLETED");
        } else if (submitted > 0 || lines.stream().anyMatch(l -> nullSafe(l.getSubmittedQty()).compareTo(BigDecimal.ZERO) > 0)) {
            session.setStatus("PARTIAL_SUBMITTED");
        } else {
            session.setStatus("SCANNING");
        }
    }

    private ReceiveNoticeDetailVo buildDetail(KingdeeReceiveBillVo bill, PdaReceiveScanSession session,
                                              List<PdaReceiveScanLine> lines, NoticeBillType billType) {
        ReceiveNoticeDetailVo vo = new ReceiveNoticeDetailVo();
        vo.setBillNo(bill.getBillNo());
        vo.setBillType(billType.getCode());
        vo.setDirection(billType.getDirection().name());
        vo.setBillTypeLabel(billType.getLabel());
        vo.setBillDate(bill.getBillDate());
        vo.setSupplierCode(bill.getSupplierCode());
        vo.setSupplierName(bill.getSupplierName());
        vo.setWarehouseCode(session != null ? session.getWarehouseCode() : bill.getWarehouseCode());
        String wmsWarehouse = vo.getWarehouseCode();
        String lineErpStock = lines.stream()
                .map(PdaReceiveScanLine::getErpStockCode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        vo.setErpWarehouseCode(erpWarehouseResolver.resolve(wmsWarehouse, lineErpStock));
        vo.setScanStatus(session != null ? session.getStatus() : "NEW");
        vo.setTotalLines(session != null ? session.getTotalLines() : lines.size());
        vo.setCheckedLines(session != null ? session.getCheckedLines() : 0);
        vo.setSubmittedLines(session != null ? session.getSubmittedLines() : 0);
        vo.setLines(lines.stream().map(this::toLineVo).collect(Collectors.toList()));
        enrichLatestErpInfo(vo);
        return vo;
    }

    private void enrichLatestErpInfo(ReceiveNoticeDetailVo vo) {
        if (vo == null || !StringUtils.hasText(vo.getBillNo())) {
            return;
        }
        PdaReceiveSubmitBatch batch = submitBatchService.latestBatchByBillNos(List.of(vo.getBillNo()))
                .get(vo.getBillNo());
        if (batch != null) {
            vo.setErpBillNo(batch.getErpBillNo());
            vo.setErpSyncStatus(batch.getErpSyncStatus());
        }
    }

    private void enrichLatestErpInfo(ReceiveNoticeListItemVo vo) {
        if (vo == null || !StringUtils.hasText(vo.getBillNo())) {
            return;
        }
        PdaReceiveSubmitBatch batch = submitBatchService.latestBatchByBillNos(List.of(vo.getBillNo()))
                .get(vo.getBillNo());
        if (batch != null) {
            vo.setErpBillNo(batch.getErpBillNo());
            vo.setErpSyncStatus(batch.getErpSyncStatus());
        }
    }

    private void enrichLatestErpInfo(List<ReceiveNoticeListItemVo> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<String> billNos = items.stream()
                .map(ReceiveNoticeListItemVo::getBillNo)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, PdaReceiveSubmitBatch> latest = submitBatchService.latestBatchByBillNos(billNos);
        for (ReceiveNoticeListItemVo item : items) {
            PdaReceiveSubmitBatch batch = latest.get(item.getBillNo());
            if (batch != null) {
                item.setErpBillNo(batch.getErpBillNo());
                item.setErpSyncStatus(batch.getErpSyncStatus());
            }
        }
    }

    private ReceiveNoticeListItemVo toListItem(KingdeeReceiveBillVo bill, PdaReceiveScanSession session,
                                               NoticeBillType billType) {
        ReceiveNoticeListItemVo vo = new ReceiveNoticeListItemVo();
        vo.setBillNo(bill.getBillNo());
        vo.setBillType(billType.getCode());
        vo.setDirection(billType.getDirection().name());
        vo.setBillTypeLabel(billType.getLabel());
        vo.setBillDate(bill.getBillDate());
        vo.setSupplierCode(bill.getSupplierCode());
        vo.setSupplierName(bill.getSupplierName());
        vo.setWarehouseCode(bill.getWarehouseCode());
        if (session != null) {
            vo.setScanStatus(session.getStatus());
            vo.setTotalLines(resolveTotalLines(bill, session));
            vo.setCheckedLines(session.getCheckedLines());
            vo.setSubmittedLines(session.getSubmittedLines());
            vo.setInProgress(!"COMPLETED".equals(session.getStatus()));
            vo.setPendingLines(Math.max(0, vo.getTotalLines() - session.getSubmittedLines()));
        } else {
            vo.setScanStatus("NEW");
            vo.setTotalLines(resolveTotalLines(bill, null));
            vo.setCheckedLines(0);
            vo.setSubmittedLines(0);
            vo.setInProgress(false);
            vo.setPendingLines(vo.getTotalLines());
        }
        return vo;
    }

    private int resolveTotalLines(KingdeeReceiveBillVo bill, PdaReceiveScanSession session) {
        if (bill.getTotalLines() != null && bill.getTotalLines() > 0) {
            return bill.getTotalLines();
        }
        if (bill.getLines() != null && !bill.getLines().isEmpty()) {
            return bill.getLines().size();
        }
        if (session != null && session.getTotalLines() != null && session.getTotalLines() > 0) {
            return session.getTotalLines();
        }
        return 0;
    }

    private ReceiveNoticeLineVo toLineVo(PdaReceiveScanLine line) {
        ReceiveNoticeLineVo vo = new ReceiveNoticeLineVo();
        vo.setLineNo(line.getLineNo());
        vo.setMaterialCode(line.getMaterialCode());
        vo.setMaterialName(line.getMaterialName());
        vo.setSpecification(line.getSpecification());
        vo.setBatchNo(line.getBatchNo());
        vo.setUnitCode(line.getUnitCode());
        vo.setErpStockCode(line.getErpStockCode());
        vo.setPlanQty(line.getPlanQty());
        BigDecimal submitted = nullSafe(line.getSubmittedQty());
        BigDecimal scanned = nullSafe(line.getScannedQty());
        if (scanned.compareTo(submitted) < 0) {
            scanned = submitted;
        }
        vo.setScannedQty(scanned);
        vo.setSubmittedQty(submitted);
        BigDecimal pending = scanned.subtract(submitted);
        vo.setPendingSubmitQty(pending.compareTo(BigDecimal.ZERO) > 0 ? pending : BigDecimal.ZERO);
        vo.setRemainQty(nullSafe(line.getPlanQty()).subtract(submitted));
        vo.setChecked(line.getChecked() != null && line.getChecked() == 1);
        vo.setScannedBarcode(line.getScannedBarcode());
        vo.setLastScanTime(line.getLastScanTime());
        return vo;
    }

    private KingdeeReceiveBillVo mockFromSession(PdaReceiveScanSession session) {
        return KingdeeReceiveBillVo.builder()
                .billNo(session.getBillNo())
                .billDate(session.getBillDate())
                .supplierCode(session.getSupplierCode())
                .supplierName(session.getSupplierName())
                .warehouseCode(session.getWarehouseCode())
                .totalLines(session.getTotalLines())
                .build();
    }

    private BigDecimal resolvePlanQty(KingdeeReceiveBillLineVo kdLine) {
        // 用料清单/领料单：planQty 已映射为可领或可退数量
        if (kdLine.getPpBomEntryId() != null || StringUtils.hasText(kdLine.getMoBillNo())
                || StringUtils.hasText(kdLine.getPpBomBillNo())
                || (kdLine.getEntryId() != null && kdLine.getEntryId() > 0
                && StringUtils.hasText(kdLine.getParentMaterialCode()))) {
            return nullSafe(kdLine.getPlanQty());
        }
        if (kdLine.getQualifiedQty() != null && kdLine.getQualifiedQty().compareTo(BigDecimal.ZERO) > 0) {
            return kdLine.getQualifiedQty();
        }
        return kdLine.getPlanQty();
    }

    private String resolveMaterialDisplayName(KingdeeReceiveBillLineVo kdLine) {
        if (StringUtils.hasText(kdLine.getMaterialName())) {
            return kdLine.getMaterialName();
        }
        if (StringUtils.hasText(kdLine.getMaterialDesc())) {
            return kdLine.getMaterialDesc();
        }
        return kdLine.getMaterialCode();
    }

    private PdaReceiveScanSession loadSession(NoticeBillType billType, String billNo) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanSession>()
                .eq(PdaReceiveScanSession::getBillType, billType.getCode())
                .eq(PdaReceiveScanSession::getDirection, billType.getDirection().name())
                .eq(PdaReceiveScanSession::getBillNo, billNo));
    }

    private Map<String, PdaReceiveScanSession> loadSessions(NoticeBillType billType, List<String> billNos) {
        if (billNos == null || billNos.isEmpty()) {
            return Map.of();
        }
        List<PdaReceiveScanSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<PdaReceiveScanSession>()
                .eq(PdaReceiveScanSession::getBillType, billType.getCode())
                .eq(PdaReceiveScanSession::getDirection, billType.getDirection().name())
                .in(PdaReceiveScanSession::getBillNo, billNos));
        Map<String, PdaReceiveScanSession> map = new HashMap<>();
        for (PdaReceiveScanSession session : sessions) {
            if (session != null && StringUtils.hasText(session.getBillNo())) {
                map.put(session.getBillNo().trim(), session);
            }
        }
        return map;
    }

    private PdaReceiveScanSession requireSession(NoticeBillType billType, String billNo) {
        PdaReceiveScanSession session = loadSession(billType, billNo);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "扫码会话不存在，请先打开单据");
        }
        return session;
    }

    private List<PdaReceiveScanLine> loadLines(NoticeBillType billType, String billNo) {
        return lineMapper.selectList(new LambdaQueryWrapper<PdaReceiveScanLine>()
                .eq(PdaReceiveScanLine::getBillType, billType.getCode())
                .eq(PdaReceiveScanLine::getDirection, billType.getDirection().name())
                .eq(PdaReceiveScanLine::getBillNo, billNo)
                .orderByAsc(PdaReceiveScanLine::getLineNo));
    }

    private String resolveErpStockCode(String wmsWarehouse, PdaReceiveScanLine line) {
        return erpWarehouseResolver.resolve(wmsWarehouse, line != null ? line.getErpStockCode() : null);
    }

    private String resolveSubmitErpStockCode(String wmsWarehouse, PdaReceiveScanLine line,
                                           KingdeeReceiveBillLineVo kdLine, String defaultErpWarehouse) {
        String lineErpStock = firstNonBlank(
                line != null ? line.getErpStockCode() : null,
                kdLine != null ? kdLine.getStockWarehouseCode() : null,
                defaultErpWarehouse);
        return erpWarehouseResolver.resolve(wmsWarehouse, lineErpStock);
    }

    /** 入库按收料分录物料仓库（金蝶 FStockId）自动分配，不分配库位 */
    private String resolveLineWarehouse(PdaReceiveScanLine line, KingdeeReceiveBillLineVo kdLine,
                                        ReceiveNoticeSubmitRequest req) {
        return firstNonBlank(
                line != null ? line.getErpStockCode() : null,
                kdLine != null ? kdLine.getStockWarehouseCode() : null,
                req != null ? req.getErpWarehouseCode() : null);
    }

    private String resolveManualInboundWarehouse(ReceiveNoticeSubmitRequest req) {
        if (req == null) {
            return null;
        }
        String erp = StringUtils.hasText(req.getErpWarehouseCode()) ? req.getErpWarehouseCode().trim() : null;
        if (StringUtils.hasText(erp)) {
            return erp;
        }
        String wms = StringUtils.hasText(req.getWarehouseCode()) ? req.getWarehouseCode().trim() : null;
        return erpWarehouseResolver.resolve(wms, null);
    }

    private KingdeeReceiveBillVo refreshKingdeeContextBeforeSubmit(NoticeBillType billType, String billNo,
                                                                   ReceiveNoticeSubmitRequest req) {
        NoticeBillProvider provider = noticeBillProviderRegistry.require(billType);
        KingdeeReceiveBillVo bill = provider.getBill(billNo);
        if (bill == null) {
            if (!provider.isErpEnabled()) {
                PdaReceiveScanSession session = loadSession(billType, billNo);
                if (session != null) {
                    return mockFromSession(session);
                }
            }
            throw new BusinessException(ErrorCode.NOT_FOUND, billType.getLabel() + "不存在或已完结: " + billNo);
        }
        PdaReceiveScanSession session = ensureSession(billType, bill);
        if (StringUtils.hasText(req.getSupplierCode())) {
            session.setSupplierCode(req.getSupplierCode().trim());
            if (StringUtils.hasText(req.getSupplierName())) {
                session.setSupplierName(req.getSupplierName());
            }
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        syncLinesFromKingdee(session, bill);
        return bill;
    }

    private KingdeeReceiveBillLineVo findKdLine(KingdeeReceiveBillVo bill, PdaReceiveScanLine line) {
        if (bill == null || bill.getLines() == null || line == null) {
            return null;
        }
        return bill.getLines().stream()
                .filter(k -> line.getLineNo() != null && line.getLineNo().equals(k.getLineNo()))
                .findFirst()
                .orElseGet(() -> bill.getLines().stream()
                        .filter(k -> StringUtils.hasText(k.getMaterialCode())
                                && k.getMaterialCode().equalsIgnoreCase(line.getMaterialCode()))
                        .findFirst()
                        .orElse(null));
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

    private Long firstNonNull(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    /**
     * 解析物料批号：扫码识别 → 条码内容 → 金蝶收料单 → 当日日期兜底。
     */
    private String resolveLineBatchNo(String primary, String barcode, String kdBatch) {
        String lot = BatchNoNormalizer.normalize(primary);
        if (!StringUtils.hasText(lot) && StringUtils.hasText(barcode)) {
            lot = BatchNoNormalizer.parseFromPipeBarcode(barcode.trim());
        }
        if (!StringUtils.hasText(lot)) {
            lot = BatchNoNormalizer.normalize(kdBatch);
        }
        if (!StringUtils.hasText(lot)) {
            lot = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        return lot;
    }

    private String resolveLocation(NoticeBillType billType, String warehouse, PdaReceiveScanLine line,
                                   String batchNoMat, boolean autoAllocate, String requestedLocation) {
        if (billType.getDirection().isInbound()) {
            return "";
        }
        if (StringUtils.hasText(requestedLocation)) {
            return requestedLocation;
        }
        Inventory stock = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouse)
                .eq(Inventory::getMaterialCode, line.getMaterialCode())
                .eq(Inventory::getBatchNo, batchNoMat)
                .gt(Inventory::getAvailableQty, BigDecimal.ZERO)
                .orderByDesc(Inventory::getAvailableQty)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        if (stock != null && StringUtils.hasText(stock.getLocationCode())) {
            return stock.getLocationCode();
        }
        stock = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouse)
                .eq(Inventory::getMaterialCode, line.getMaterialCode())
                .gt(Inventory::getAvailableQty, BigDecimal.ZERO)
                .orderByDesc(Inventory::getAvailableQty)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        if (stock != null && StringUtils.hasText(stock.getLocationCode())) {
            return stock.getLocationCode();
        }
        throw new BusinessException(ErrorCode.CONFLICT, "未找到可出库库存: " + line.getMaterialCode(), "NO_STOCK");
    }

    private void validateInboundQtyAgainstKingdee(List<PdaReceiveScanLine> lines, KingdeeReceiveBillVo kdBill) {
        for (PdaReceiveScanLine line : lines) {
            BigDecimal qty = nullSafe(line.getScannedQty()).subtract(nullSafe(line.getSubmittedQty()));
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            KingdeeReceiveBillLineVo kdLine = findKdLine(kdBill, line);
            BigDecimal remain = resolveKingdeeRemainInStock(kdLine);
            if (remain != null && qty.compareTo(remain) > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        String.format("第%d行「%s」本次入库 %s 超过收料单剩余可入库 %s",
                                line.getLineNo(),
                                firstNonBlank(line.getMaterialName(), line.getMaterialCode()),
                                qty.stripTrailingZeros().toPlainString(),
                                remain.stripTrailingZeros().toPlainString()),
                        "ERP_IN_STOCK_QTY_EXCEEDED");
            }
        }
    }

    private BigDecimal resolveKingdeeRemainInStock(KingdeeReceiveBillLineVo kdLine) {
        if (kdLine == null) {
            return null;
        }
        if (kdLine.getRemainInStockBaseQty() != null
                && kdLine.getRemainInStockBaseQty().compareTo(BigDecimal.ZERO) >= 0) {
            return kdLine.getRemainInStockBaseQty();
        }
        BigDecimal qualified = nullSafe(kdLine.getQualifiedQty());
        BigDecimal joined = nullSafe(kdLine.getInStockJoinBaseQty());
        if (qualified.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remain = qualified.subtract(joined);
            return remain.compareTo(BigDecimal.ZERO) > 0 ? remain : BigDecimal.ZERO;
        }
        return null;
    }

    private PdaReceiveScanSession loadSession(String billNo) {
        return loadSession(NoticeBillType.PURCHASE_RECEIVE, billNo);
    }

    private PdaReceiveScanSession requireSession(String billNo) {
        return requireSession(NoticeBillType.PURCHASE_RECEIVE, billNo);
    }

    private List<PdaReceiveScanLine> loadLines(String billNo) {
        return loadLines(NoticeBillType.PURCHASE_RECEIVE, billNo);
    }

    private PdaReceiveScanLine pickScanLine(NoticeBillType billType, String billNo,
                                            String materialCode, BigDecimal scanQty) {
        List<PdaReceiveScanLine> candidates = lineMapper.selectList(new LambdaQueryWrapper<PdaReceiveScanLine>()
                .eq(PdaReceiveScanLine::getBillType, billType.getCode())
                .eq(PdaReceiveScanLine::getDirection, billType.getDirection().name())
                .eq(PdaReceiveScanLine::getBillNo, billNo)
                .eq(PdaReceiveScanLine::getMaterialCode, materialCode)
                .orderByAsc(PdaReceiveScanLine::getLineNo));
        if (candidates.isEmpty()) {
            return null;
        }
        BigDecimal qty = scanQty == null || scanQty.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE : scanQty;
        List<PdaReceiveScanLine> open = candidates.stream()
                .filter(l -> nullSafe(l.getPlanQty()).subtract(nullSafe(l.getSubmittedQty())).compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (open.isEmpty()) {
            return null;
        }
        for (PdaReceiveScanLine line : open) {
            BigDecimal remain = nullSafe(line.getPlanQty()).subtract(nullSafe(line.getSubmittedQty()));
            if (remain.compareTo(qty) == 0) {
                return line;
            }
        }
        return open.stream()
                .filter(l -> nullSafe(l.getPlanQty()).subtract(nullSafe(l.getSubmittedQty())).compareTo(qty) >= 0)
                .min((a, b) -> {
                    BigDecimal ra = nullSafe(a.getPlanQty()).subtract(nullSafe(a.getSubmittedQty()));
                    BigDecimal rb = nullSafe(b.getPlanQty()).subtract(nullSafe(b.getSubmittedQty()));
                    return ra.compareTo(rb);
                })
                .orElse(open.get(0));
    }

    private BillMaterialMatch resolveMaterialOnBill(String barcode, List<PdaReceiveScanLine> billLines,
                                                    BarcodeRecognizeResult recognized) {
        String raw = barcode != null ? barcode.trim() : "";
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条码不能为空", "BARCODE_EMPTY");
        }
        if (billLines == null || billLines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "收料单明细未加载完成，请返回后重新进入",
                    "BILL_LINES_NOT_READY");
        }

        if (raw.contains("|")) {
            String pipeBatch = BatchNoNormalizer.parseFromPipeBarcode(raw);
            String pipeMaterial = raw.split("\\|", -1)[0].trim();
            if (StringUtils.hasText(pipeMaterial)) {
                boolean onBill = billLines.stream()
                        .anyMatch(l -> pipeMaterial.equalsIgnoreCase(l.getMaterialCode()));
                if (onBill) {
                    return new BillMaterialMatch(pipeMaterial, pipeBatch, "PIPE_FORMAT");
                }
            }
        }

        for (PdaReceiveScanLine line : billLines) {
            if (line.getMaterialCode() != null && line.getMaterialCode().equalsIgnoreCase(raw)) {
                return new BillMaterialMatch(line.getMaterialCode(),
                        BatchNoNormalizer.normalize(line.getBatchNo()), "EXACT_CODE");
            }
        }

        for (PdaReceiveScanLine line : billLines) {
            String code = line.getMaterialCode();
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String batch = line.getBatchNo() != null ? line.getBatchNo() : "";
            if ((code + batch).equalsIgnoreCase(raw)) {
                return new BillMaterialMatch(code, BatchNoNormalizer.normalize(batch), "CODE_BATCH_CONCAT");
            }
        }

        PdaReceiveScanLine prefixHit = null;
        if (!raw.contains("|")) {
            for (PdaReceiveScanLine line : billLines) {
                String code = line.getMaterialCode();
                if (!StringUtils.hasText(code)) {
                    continue;
                }
                if (raw.length() > code.length() && raw.regionMatches(true, 0, code, 0, code.length())) {
                    if (prefixHit == null || code.length() > prefixHit.getMaterialCode().length()) {
                        prefixHit = line;
                    }
                }
            }
            if (prefixHit != null) {
                String code = prefixHit.getMaterialCode();
                String suffix = raw.substring(code.length());
                String batchNo = StringUtils.hasText(suffix)
                        ? BatchNoNormalizer.normalize(suffix)
                        : BatchNoNormalizer.normalize(prefixHit.getBatchNo());
                return new BillMaterialMatch(code, batchNo, "BILL_PREFIX");
            }
        }

        BarcodeRecognizeResult rec = recognized;
        if (rec == null) {
            rec = barcodeRecognizeService.recognize(raw);
        }
        String materialCode = rec.getMaterialCode();
        if (!StringUtils.hasText(materialCode)) {
            PdaReceiveScanLine nameHit = matchLineByNameAndSpec(
                    billLines, rec.getMaterialName(), rec.getSpecification());
            if (nameHit != null) {
                materialCode = nameHit.getMaterialCode();
            }
        }
        if (!StringUtils.hasText(materialCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "条码格式无法识别，请扫描物料标签二维码或物料编码",
                    "BARCODE_PARSE_FAILED",
                    Map.of("barcode", raw));
        }

        boolean onBill = isMaterialOnBill(billLines, materialCode);
        if (!onBill) {
            PdaReceiveScanLine nameHit = matchLineByNameAndSpec(
                    billLines, rec.getMaterialName(), rec.getSpecification());
            if (nameHit != null) {
                materialCode = nameHit.getMaterialCode();
                onBill = true;
            }
        }
        if (!onBill) {
            String billCodes = billLines.stream()
                    .map(PdaReceiveScanLine::getMaterialCode)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(5)
                    .collect(Collectors.joining("、"));
            String suffix = billLines.size() > 5 ? " 等" : "";
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    String.format("物料 %s 不在本收料通知单中。本单物料：%s%s", materialCode, billCodes, suffix),
                    "MATERIAL_NOT_ON_BILL",
                    Map.of("recognizedMaterialCode", materialCode, "barcode", raw));
        }
        String batchNo = BatchNoNormalizer.normalize(rec.getBatchNo());
        if (!StringUtils.hasText(batchNo)) {
            batchNo = BatchNoNormalizer.parseFromPipeBarcode(raw);
        }
        return new BillMaterialMatch(materialCode, batchNo, rec.getParseMode());
    }

    private boolean isMaterialOnBill(List<PdaReceiveScanLine> billLines, String materialCode) {
        if (!StringUtils.hasText(materialCode) || billLines == null) {
            return false;
        }
        for (PdaReceiveScanLine line : billLines) {
            if (materialCode.equalsIgnoreCase(line.getMaterialCode())) {
                return true;
            }
        }
        return false;
    }

    private PdaReceiveScanLine matchLineByNameAndSpec(List<PdaReceiveScanLine> billLines,
                                                      String materialName, String specification) {
        if (!StringUtils.hasText(materialName) || billLines == null || billLines.isEmpty()) {
            return null;
        }
        String name = materialName.trim();
        List<PdaReceiveScanLine> byName = billLines.stream()
                .filter(l -> StringUtils.hasText(l.getMaterialName())
                        && name.equalsIgnoreCase(l.getMaterialName().trim()))
                .toList();
        if (byName.isEmpty()) {
            byName = billLines.stream()
                    .filter(l -> StringUtils.hasText(l.getMaterialName())
                            && l.getMaterialName().trim().contains(name))
                    .toList();
        }
        if (byName.isEmpty()) {
            return null;
        }
        if (byName.size() == 1) {
            return byName.get(0);
        }
        if (StringUtils.hasText(specification)) {
            String spec = specification.trim();
            return byName.stream()
                    .filter(l -> StringUtils.hasText(l.getSpecification())
                            && spec.equalsIgnoreCase(l.getSpecification().trim()))
                    .findFirst()
                    .orElse(byName.get(0));
        }
        return byName.get(0);
    }

    private BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Comparator<ReceiveNoticeListItemVo> noticeNewestFirst() {
        return Comparator
                .comparing(ReceiveNoticeListItemVo::getBillDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ReceiveNoticeListItemVo::getBillNo, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private boolean isMockReceiveBillNo(String billNo) {
        return StringUtils.hasText(billNo) && billNo.trim().matches("(?i)SLD\\d+");
    }

    private boolean contains(String text, String kw) {
        return text != null && text.toLowerCase().contains(kw);
    }

    private String generateSubmitBatchNo() {
        return "RSB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", BATCH_SEQ.getAndIncrement() % 10000);
    }

    private String generateRecordNo() {
        return "RIN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", RECORD_SEQ.getAndIncrement() % 10000);
    }
}
