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
import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.integration.kingdee.KingdeeErpWarehouseResolver;
import com.wms.integration.kingdee.KingdeeReceiveRemainQty;
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
import com.wms.inventory.service.LocationAllocationService;
import com.wms.pdainbound.entity.PdaInboundRecord;
import com.wms.pdainbound.mapper.PdaInboundRecordMapper;
import com.wms.mobile.service.MobileMessageService;
import com.wms.pdareceive.dto.*;
import com.wms.pdareceive.entity.PdaReceiveScanLine;
import com.wms.pdareceive.entity.PdaReceiveScanSession;
import com.wms.pdareceive.entity.PdaReceiveSubmitBatch;
import com.wms.pdabilllock.dto.PdaBillLockVo;
import com.wms.pdabilllock.entity.PdaBillLock;
import com.wms.pdabilllock.service.PdaBillLockService;
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
    /** 提交批次视为「同步进行中」的时间窗（分钟） */
    private static final int IN_FLIGHT_BATCH_MINUTES = 5;

    private final NoticeBillProviderRegistry noticeBillProviderRegistry;
    private final PdaReceiveScanSessionMapper sessionMapper;
    private final PdaReceiveScanLineMapper lineMapper;
    private final PdaReceiveSubmitBatchMapper submitBatchMapper;
    private final PdaInboundRecordMapper inboundRecordMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryService inventoryService;
    private final LocationAllocationService locationAllocationService;
    private final BarcodeTraceLinkService barcodeTraceLinkService;
    private final BarcodeRecognizeService barcodeRecognizeService;
    private final PdaReceiveSubmitBatchService submitBatchService;
    private final PdaErpSyncAsyncService erpSyncAsyncService;
    private final KingdeeErpWarehouseResolver erpWarehouseResolver;
    private final KingdeeCloudService kingdeeCloudService;
    private final WmsPdaProperties pdaProperties;
    private final PdaShortCache pdaShortCache;
    private final PdaBillLockService billLockService;

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
            // 空列表不信任短缓存（避免错误过滤结果长期占坑）
            if (visible != null && visible.isEmpty()) {
                visible = null;
                pdaShortCache.evict(visibleCacheKey);
            }
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
            // 纠正状态：有提交量/可处理全 0 → 已完成并从列表剔除；仅待提交 → 扫码中
            reconcileSessionStatuses(sessionMap.values());

            Map<String, ReceiveNoticeListItemVo> merged = new LinkedHashMap<>();
            for (KingdeeReceiveBillVo bill : kdRecords) {
                if (bill == null || !StringUtils.hasText(bill.getBillNo())) {
                    continue;
                }
                PdaReceiveScanSession session = sessionMap.get(bill.getBillNo().trim());
                // 金蝶列表仍返回该单（有余量/未关闭）时必须展示，不能因本地会话曾完结而当成不存在
                session = reopenSessionForUnauditedBill(billType, session);
                if (bill.getLines() != null && !bill.getLines().isEmpty()) {
                    bill.setLines(null);
                }
                merged.put(bill.getBillNo(), toListItem(bill, session, billType));
            }

            List<PdaReceiveScanSession> openSessions = sessionMapper.selectList(new LambdaQueryWrapper<PdaReceiveScanSession>()
                    .eq(PdaReceiveScanSession::getBillType, billType.getCode())
                    .eq(PdaReceiveScanSession::getDirection, billType.getDirection().name())
                    .in(PdaReceiveScanSession::getStatus, List.of("NEW", "SCANNING"))
                    .orderByDesc(PdaReceiveScanSession::getUpdateTime));
            reconcileSessionStatuses(openSessions);
            for (PdaReceiveScanSession session : openSessions) {
                if (!StringUtils.hasText(session.getBillNo()) || isMockReceiveBillNo(session.getBillNo())) {
                    continue;
                }
                if (isSessionFinished(session.getStatus())) {
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
                    .filter(v -> keepKingdeeListedBill(billType, v))
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
            if (pdaProperties.getShortCacheTtlSeconds() > 0 && !visible.isEmpty()) {
                pdaShortCache.put(visibleCacheKey, visible,
                        Duration.ofSeconds(Math.max(60, pdaProperties.getShortCacheTtlSeconds())));
            }
        } else {
            // 短缓存命中时仍按最新会话剔除已完结（含可处理数归零）
            visible = filterVisibleByLatestSession(billType, visible);
        }

        long total = visible.size();
        int from = (int) Math.max(0, (page - 1) * pageSize);
        int to = (int) Math.min(visible.size(), from + (int) pageSize);
        List<ReceiveNoticeListItemVo> slice = from < visible.size() ? visible.subList(from, to) : List.of();
        enrichLockInfo(billType, slice);
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
        if (!StringUtils.hasText(no)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单据号不能为空");
        }
        PdaReceiveScanSession existingSession = loadSession(billType, no);
        List<PdaReceiveScanLine> existingLines = loadLines(billType, no);
        // 退料/生产入库/补料数量曾映射为 0：强制回源金蝶重算，避免短缓存把 0 继续返回
        boolean planQtyAllZero = existingLines != null && !existingLines.isEmpty()
                && existingLines.stream().allMatch(l -> nullSafe(l.getPlanQty()).compareTo(BigDecimal.ZERO) <= 0);
        boolean needQtyRepair = (billType == NoticeBillType.PRODUCTION_RETURN
                || billType == NoticeBillType.PRODUCTION_IN
                || billType == NoticeBillType.PRODUCTION_RET_STOCK
                || billType == NoticeBillType.PRODUCTION_FEED
                || billType == NoticeBillType.OUTSOURCE_FEED
                || billType == NoticeBillType.PRODUCTION_ISSUE
                || billType == NoticeBillType.OUTSOURCE_ISSUE)
                && planQtyAllZero;
        if (needQtyRepair) {
            forceRefresh = true;
            pdaShortCache.evict("bill:" + billType.getCode() + ":" + no.toUpperCase());
        }
        int preferSec = pdaProperties.getDetailLocalPreferSeconds();
        if (!forceRefresh
                && preferSec > 0
                && existingSession != null
                && !isSessionFinished(existingSession.getStatus())
                && existingLines != null
                && !existingLines.isEmpty()
                && existingSession.getUpdateTime() != null
                && existingSession.getUpdateTime().isAfter(LocalDateTime.now().minusSeconds(preferSec))) {
            billLockService.acquire(billType.getCode(), no);
            reconcileSessionStatus(existingSession, existingLines);
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
                    billLockService.acquire(billType.getCode(), no);
                    return buildDetail(mockFromSession(existingSession), existingSession, existingLines, billType);
                }
                throw new BusinessException(ErrorCode.NOT_FOUND,
                        "金蝶未启用，无法查询" + billType.getLabel() + ": " + no + "。请将 kingdee.cloud.enabled 设为 true");
            }
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    billType.getLabel() + "不存在、未审核或金蝶查询失败: " + no);
        }
        PdaReceiveScanSession session = ensureSession(billType, bill);
        // 金蝶仍能查到未审核单据（反审核回到审核中）时重开本地会话，允许重新扫码领料
        session = reopenSessionForUnauditedBill(billType, session);
        syncLinesFromKingdee(session, bill);
        billLockService.acquire(billType.getCode(), no);
        return buildDetail(bill, session, loadLines(billType, no), billType);
    }

    public PdaBillLockVo heartbeatLock(NoticeBillType billType, String billNo) {
        return billLockService.heartbeat(billType.getCode(), billNo);
    }

    public void releaseLock(NoticeBillType billType, String billNo) {
        billLockService.release(billType.getCode(), billNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo scanLine(String billNo, ReceiveNoticeScanRequest req) {
        return scanLine(NoticeBillType.PURCHASE_RECEIVE, billNo, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo scanLine(NoticeBillType billType, String billNo, ReceiveNoticeScanRequest req) {
        billLockService.assertHeld(billType.getCode(), billNo);
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
        BigDecimal submittedAux = nullSafe(line.getSubmittedAuxQty());
        BigDecimal remainAux = nullSafe(line.getPlanAuxQty()).subtract(submittedAux);
        if (remainAux.compareTo(BigDecimal.ZERO) < 0) {
            remainAux = BigDecimal.ZERO;
        }
        // 主录件数：以计价/件数剩余判断是否收满；已处理来自金蝶时只要可处理>0仍可扫
        boolean lineFull;
        if (isMultiUnit(line) && isInputMapsToAux(line)) {
            if (nullSafe(line.getPlanAuxQty()).compareTo(BigDecimal.ZERO) <= 0
                    && submittedAux.compareTo(BigDecimal.ZERO) <= 0
                    && remain.compareTo(BigDecimal.ZERO) <= 0) {
                // 计划未下发：允许先扫
                lineFull = false;
            } else if (nullSafe(line.getPlanAuxQty()).compareTo(BigDecimal.ZERO) > 0) {
                lineFull = remainAux.compareTo(BigDecimal.ZERO) <= 0;
            } else {
                lineFull = remain.compareTo(BigDecimal.ZERO) <= 0
                        && submitted.compareTo(BigDecimal.ZERO) > 0;
            }
        } else if (nullSafe(line.getPlanQty()).compareTo(BigDecimal.ZERO) <= 0
                && submitted.compareTo(BigDecimal.ZERO) <= 0) {
            lineFull = false;
        } else {
            lineFull = remain.compareTo(BigDecimal.ZERO) <= 0;
        }
        if (lineFull) {
            throw new BusinessException(ErrorCode.CONFLICT, "该物料已收满", "LINE_ALREADY_FULL");
        }
        String resolvedBatch = resolveLineBatchNo(
                match.batchNo(), raw, null);
        if (StringUtils.hasText(resolvedBatch)) {
            line.setBatchNo(resolvedBatch);
        }
        line.setChecked(1);

        // 标签数量按件数（主单位）累加，再换算到 KG
        if (isMultiUnit(line) && isInputMapsToAux(line)) {
            BigDecimal planAux = nullSafe(line.getPlanAuxQty());
            if (remain.compareTo(BigDecimal.ZERO) < 0) {
                remain = BigDecimal.ZERO;
            }
            BigDecimal currentAuxPending = nullSafe(line.getScannedAuxQty()).subtract(submittedAux);
            if (currentAuxPending.compareTo(BigDecimal.ZERO) < 0) {
                currentAuxPending = BigDecimal.ZERO;
            }
            BigDecimal nextAuxPending = currentAuxPending.add(scanQty);
            if (planAux.compareTo(BigDecimal.ZERO) > 0 && nextAuxPending.compareTo(remainAux) > 0) {
                nextAuxPending = remainAux;
            }
            BigDecimal nextStockPending = convertByPlanRate(nextAuxPending, planAux, line.getPlanQty());
            if (nullSafe(line.getPlanQty()).compareTo(BigDecimal.ZERO) > 0
                    && nextStockPending.compareTo(remain) > 0) {
                nextStockPending = remain;
                nextAuxPending = convertByPlanRate(nextStockPending, line.getPlanQty(), planAux);
            }
            if (nullSafe(line.getPlanQty()).compareTo(BigDecimal.ZERO) <= 0) {
                nextStockPending = nextAuxPending;
            }
            line.setScannedAuxQty(submittedAux.add(nextAuxPending));
            line.setScannedQty(submitted.add(nextStockPending));
            if (nullSafe(line.getPlanQty()).compareTo(BigDecimal.ZERO) <= 0) {
                line.setPlanQty(nullSafe(line.getScannedQty()));
            }
            if (planAux.compareTo(BigDecimal.ZERO) <= 0) {
                line.setPlanAuxQty(nullSafe(line.getScannedAuxQty()));
            }
        } else {
            if (remain.compareTo(BigDecimal.ZERO) < 0) {
                remain = BigDecimal.ZERO;
            }
            BigDecimal currentPending = nullSafe(line.getScannedQty()).subtract(submitted);
            if (currentPending.compareTo(BigDecimal.ZERO) < 0) {
                currentPending = BigDecimal.ZERO;
            }
            BigDecimal nextPending = currentPending.add(scanQty);
            if (nullSafe(line.getPlanQty()).compareTo(BigDecimal.ZERO) <= 0) {
                // 计划未下发：按本次累计扫码抬升计划，避免无法累加
                line.setPlanQty(submitted.add(nextPending));
            } else if (nextPending.compareTo(remain) > 0) {
                nextPending = remain;
            }
            line.setScannedQty(submitted.add(nextPending));
            if (isMultiUnit(line)) {
                BigDecimal nextAuxPending = resolvePendingAuxQty(line, nextPending, null);
                line.setScannedAuxQty(submittedAux.add(nextAuxPending));
            }
        }
        line.setScannedBarcode(req.getBarcodeContent());
        line.setLastScanTime(LocalDateTime.now());
        line.setUpdateTime(LocalDateTime.now());
        lineMapper.updateById(line);

        PdaReceiveScanSession session = requireSession(billType, billNo);
        session.setLastScanTime(LocalDateTime.now());
        session.setDeviceNo(req.getDeviceNo());
        markQtyChanged(billType, billNo, session);
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
        billLockService.assertHeld(billType.getCode(), billNo);
        LoginUser user = SecurityUtils.currentUser();
        KingdeeReceiveBillVo kdBill = refreshKingdeeContextBeforeSubmit(billType, billNo, req);
        PdaReceiveScanSession session = requireSession(billType, billNo);
        // 双单位先对齐待提交量，再筛选：已勾选且库存/计价任一侧有待提交即可
        // （不以会话 COMPLETED 直接拦截，避免金蝶回写后仍有待提交量却无法提交）
        List<PdaReceiveScanLine> allLines = loadLines(billType, billNo);
        for (PdaReceiveScanLine line : allLines) {
            repairDualUnitPending(line);
        }
        List<PdaReceiveScanLine> lines = allLines.stream()
                .filter(l -> l.getChecked() != null && l.getChecked() == 1)
                .filter(this::hasPendingSubmitQty)
                .collect(Collectors.toList());
        if (lines.isEmpty()) {
            String action = billType.getDirection().isInbound() ? "入库" : "出库";
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先扫码或手动填写数量后再提交" + action);
        }

        boolean inbound = billType.getDirection().isInbound();
        // 未审核工作流 / 已审源单下推：不改 WMS 库存，仅驱动金蝶
        boolean auditExistingBill = billType.isErpConfirmWithoutWmsStock();
        if (inbound && !auditExistingBill) {
            validateInboundQtyAgainstKingdee(lines, kdBill);
        }

        String supplierCode = firstNonBlank(req.getSupplierCode(), session.getSupplierCode(), kdBill.getSupplierCode());
        String supplierName = firstNonBlank(req.getSupplierName(), session.getSupplierName(), kdBill.getSupplierName());
        if (inbound && !auditExistingBill && (billType == NoticeBillType.PURCHASE_RECEIVE
                || billType == NoticeBillType.SALES_RETURN)
                && !StringUtils.hasText(supplierCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    billType == NoticeBillType.SALES_RETURN
                            ? "缺少退货客户编码，请返回列表重新进入退货通知单"
                            : "缺少供应商编码，请返回列表重新进入收料单",
                    billType == NoticeBillType.SALES_RETURN ? "MISSING_CUSTOMER" : "MISSING_SUPPLIER");
        }

        String defaultWarehouse = StringUtils.hasText(req.getWarehouseCode())
                ? req.getWarehouseCode() : session.getWarehouseCode();
        // 仅显式 true 时自动分配；默认不选库位（可仅仓库入库）
        boolean autoAllocate = Boolean.TRUE.equals(req.getAutoAllocateLocation());
        String requestedLocation = StringUtils.hasText(req.getLocationCode())
                ? req.getLocationCode().trim() : "";
        String batchNo = generateSubmitBatchNo();
        BigDecimal totalQty = BigDecimal.ZERO;
        List<String> recordNos = new ArrayList<>();
        List<KingdeePickMtrlRequest.Line> pickMtrlLines = new ArrayList<>();
        List<KingdeeSubPickMtrlRequest.Line> subPickMtrlLines = new ArrayList<>();
        List<KingdeeReturnMtrlRequest.Line> returnMtrlLines = new ArrayList<>();
        List<KingdeeSubReturnMtrlRequest.Line> subReturnMtrlLines = new ArrayList<>();
        // 金蝶失败时还原「已处理」，保证可处理数不变
        Map<Integer, BigDecimal[]> submittedSnapshot = new LinkedHashMap<>();
        List<InventoryChangeCommand> inventoryUndoCommands = new ArrayList<>();

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
            repairDualUnitPending(line);
            BigDecimal qty = nullSafe(line.getScannedQty()).subtract(nullSafe(line.getSubmittedQty()));
            if (qty.compareTo(BigDecimal.ZERO) <= 0 && isMultiUnit(line)) {
                BigDecimal auxPending = nullSafe(line.getScannedAuxQty()).subtract(nullSafe(line.getSubmittedAuxQty()));
                if (auxPending.compareTo(BigDecimal.ZERO) > 0) {
                    qty = convertByPlanRate(auxPending, line.getPlanAuxQty(), line.getPlanQty());
                    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                        qty = auxPending;
                    }
                    line.setScannedQty(nullSafe(line.getSubmittedQty()).add(qty));
                    line.setUpdateTime(LocalDateTime.now());
                    lineMapper.updateById(line);
                }
            }
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // 提交前快照：金蝶失败时还原已处理，可处理保持提交前
            submittedSnapshot.putIfAbsent(line.getLineNo(), new BigDecimal[]{
                    nullSafe(line.getSubmittedQty()),
                    nullSafe(line.getSubmittedAuxQty())
            });
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
            Map<String, String> autoWarehouseCache = new HashMap<>();
            String warehouse;
            String locationCode;
            String erpStockCode;
            if (auditExistingBill) {
                // 以金蝶为准：不改 WMS 库存；发货通知需落库批号/仓库供下推补全
                warehouse = firstNonBlank(
                        line.getErpStockCode(),
                        kdLine != null ? kdLine.getStockWarehouseCode() : null,
                        defaultWarehouse);
                locationCode = StringUtils.hasText(req.getLocationCode()) ? req.getLocationCode().trim() : "";
                erpStockCode = firstNonBlank(warehouse, req.getErpWarehouseCode());
                if (billType == NoticeBillType.SALES_DELIVERY) {
                    erpStockCode = erpWarehouseResolver.resolve(
                            defaultWarehouse,
                            firstNonBlank(
                                    line.getErpStockCode(),
                                    kdLine != null ? kdLine.getStockWarehouseCode() : null,
                                    req.getErpWarehouseCode()));
                    boolean lineChanged = false;
                    if (StringUtils.hasText(erpStockCode) && !erpStockCode.equals(line.getErpStockCode())) {
                        line.setErpStockCode(erpStockCode);
                        lineChanged = true;
                    }
                    if (StringUtils.hasText(batchNoMat) && !batchNoMat.equals(line.getBatchNo())) {
                        line.setBatchNo(batchNoMat);
                        lineChanged = true;
                    }
                    if (lineChanged) {
                        line.setUpdateTime(LocalDateTime.now());
                        lineMapper.updateById(line);
                    }
                }
            } else if (inbound) {
                if (autoAssignWarehouse) {
                    warehouse = resolveLineWarehouse(line, kdLine, req, autoWarehouseCache);
                    if (!StringUtils.hasText(warehouse)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "第" + line.getLineNo() + "行缺少仓库，请手动选仓或维护物料/生产订单仓库",
                                "MISSING_ERP_STOCK");
                    }
                    erpStockCode = warehouse;
                    if (!warehouse.equals(line.getErpStockCode())) {
                        line.setErpStockCode(warehouse);
                        line.setUpdateTime(LocalDateTime.now());
                        lineMapper.updateById(line);
                    }
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
                locationCode = resolveInboundLocationCode(
                        warehouse, line.getMaterialCode(), batchNoMat, autoAllocate, requestedLocation);
            } else {
                Inventory pickStock = resolveOutboundStock(defaultWarehouse, line, kdLine, batchNoMat, req.getLocationCode());
                warehouse = pickStock.getWarehouseCode();
                locationCode = pickStock.getLocationCode() != null ? pickStock.getLocationCode() : "";
                erpStockCode = resolveSubmitErpStockCode(warehouse, line, kdLine, req.getErpWarehouseCode());
            }
            String unitCode = firstNonBlank(line.getUnitCode(), kdLine != null ? kdLine.getUnitCode() : null);
            String recordNo = generateRecordNo();

            if (auditExistingBill) {
                // 仅更新扫码进度；ERP 侧回写实发/实退后立即审核（含部分领退）
            } else if (inbound) {
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
                if (isMultiUnit(line)) {
                    BigDecimal auxPending = nullSafe(line.getScannedAuxQty()).subtract(nullSafe(line.getSubmittedAuxQty()));
                    if (auxPending.compareTo(BigDecimal.ZERO) <= 0) {
                        auxPending = resolvePendingAuxQty(line, qty, null);
                    }
                    record.setAuxUnitCode(line.getAuxUnitCode());
                    record.setAuxQuantity(auxPending);
                }
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
                inventoryUndoCommands.add(InventoryChangeCommand.builder()
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
                        .remark("ERP失败回滚:" + line.getScannedBarcode())
                        .build());
                barcodeTraceLinkService.linkPdaInbound(
                        line.getScannedBarcode(), recordNo, changeResult.getTransactionNo(),
                        line.getMaterialCode(), batchNoMat, null);
                if (billType == NoticeBillType.OUTSOURCE_RETURN) {
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
                inventoryUndoCommands.add(InventoryChangeCommand.builder()
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
                        .remark("ERP失败回滚:" + line.getScannedBarcode())
                        .build());
                if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
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
            if (isMultiUnit(line)) {
                BigDecimal auxPending = nullSafe(line.getScannedAuxQty()).subtract(nullSafe(line.getSubmittedAuxQty()));
                if (auxPending.compareTo(BigDecimal.ZERO) <= 0) {
                    auxPending = resolvePendingAuxQty(line, qty, null);
                }
                line.setSubmittedAuxQty(nullSafe(line.getSubmittedAuxQty()).add(auxPending));
                if (nullSafe(line.getScannedAuxQty()).compareTo(nullSafe(line.getSubmittedAuxQty())) < 0) {
                    line.setScannedAuxQty(line.getSubmittedAuxQty());
                }
            }
            line.setUpdateTime(LocalDateTime.now());
            lineMapper.updateById(line);
            totalQty = totalQty.add(qty);
            recordNos.add(recordNo);
        }

        batch.setTotalQty(totalQty);
        int workflowLines = auditExistingBill
                ? recordNos.size()
                : (!pickMtrlLines.isEmpty()
                ? pickMtrlLines.size()
                : (!subPickMtrlLines.isEmpty() ? subPickMtrlLines.size() : recordNos.size()));
        batch.setLineCount(inbound && !auditExistingBill ? recordNos.size() : workflowLines);
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
        result.put("message", resolveSubmitMessage(billType, auditExistingBill, inbound));

        // 未审核工作流 / 发货通知下推：不 Save 新建；收料/退货通知仍走新建入库
        boolean needErp = (billType == NoticeBillType.PURCHASE_RECEIVE && !recordNos.isEmpty())
                || (billType == NoticeBillType.PRODUCTION_IN && !recordNos.isEmpty())
                || (billType == NoticeBillType.SALES_RETURN && !recordNos.isEmpty())
                || auditExistingBill;
        if (needErp) {
            evictBillCaches(billType, billNo);
            dispatchErpSync(billType, batchNo, result,
                    recordNos, returnMtrlLines, subReturnMtrlLines, pickMtrlLines, subPickMtrlLines);
            String erpStatus = String.valueOf(result.getOrDefault("erpSyncStatus", ""));
            // 同步失败（非 SUCCESS）；异步 PENDING 不在此还原（当前默认 async=false）
            if (!"SUCCESS".equalsIgnoreCase(erpStatus)
                    && !Boolean.TRUE.equals(result.get("erpSyncAsync"))) {
                restoreSubmittedQtyAfterErpFail(billType, billNo, submittedSnapshot);
                undoInventoryAfterErpFail(inbound, auditExistingBill, inventoryUndoCommands);
                markInboundRecordsFailedAndDeleted(batchNo);
                refreshSessionCounters(session);
                session.setUpdateTime(LocalDateTime.now());
                sessionMapper.updateById(session);
                result.put("scanStatus", session.getStatus());
                result.put("qtyUnchanged", true);
                result.put("erpSyncStatus", "FAILED");
                String failMsg = firstNonBlank(
                        String.valueOf(result.getOrDefault("erpSyncMessage", "")),
                        String.valueOf(result.getOrDefault("message", "")),
                        "金蝶同步失败，已处理/可处理数量未变更");
                result.put("erpSyncMessage", failMsg);
                result.put("message", failMsg);
                return result;
            }
        }
        // 提交即审核的单据（生产领料）：金蝶已审核，本单在 WMS 一并完结
        if (billType.isSubmitThenAuditBill() && !isSessionFinished(session.getStatus())) {
            session.setStatus("COMPLETED");
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
            result.put("scanStatus", session.getStatus());
            evictListCaches(billType);
        }
        // 仅金蝶成功（或不需要同步）后才允许完结并释放锁
        if (isSessionFinished(session.getStatus())) {
            billLockService.forceRelease(billType.getCode(), billNo);
        }
        return result;
    }

    private String resolveSubmitMessage(NoticeBillType billType, boolean auditExistingBill, boolean inbound) {
        if (billType == NoticeBillType.PURCHASE_RECEIVE) {
            return "已提交至WMS，正在同步金蝶";
        }
        if (billType == NoticeBillType.PRODUCTION_IN) {
            return "汇报入库已确认，正在生成金蝶生产入库单并反写审核";
        }
        if (billType == NoticeBillType.PRODUCTION_RET_STOCK) {
            return "退库已确认，正在提交并审核金蝶生产退库单";
        }
        if (billType == NoticeBillType.PRODUCTION_ISSUE) {
            return "领料已确认，正在回写金蝶实发并审核";
        }
        if (billType == NoticeBillType.PRODUCTION_FEED) {
            return "补料已确认，正在回写金蝶实发并工作流审批（支持部分补料）";
        }
        if (billType == NoticeBillType.OUTSOURCE_FEED) {
            return "补料已确认，正在回写金蝶实发并工作流审批（支持部分补料）";
        }
        if (billType == NoticeBillType.PRODUCTION_RETURN) {
            return "退料已确认，正在回写金蝶实退并审核（支持部分退料）";
        }
        if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            return "退料已确认，正在回写金蝶实退并审核（支持部分退料）";
        }
        if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            return "领料已确认，正在回写金蝶实发并审核（支持部分领料）";
        }
        if (billType == NoticeBillType.OTHER_IN) {
            return "入库已确认，正在提交并审核金蝶其他入库单";
        }
        if (billType == NoticeBillType.OTHER_OUT) {
            return "出库已确认，正在提交并审核金蝶其他出库单";
        }
        if (billType == NoticeBillType.PURCHASE_RETURN) {
            return "退货已确认，正在提交并审核金蝶采购退料单";
        }
        if (billType == NoticeBillType.SALES_DELIVERY) {
            return "发货已确认，正在下推并审核金蝶销售出库单";
        }
        if (billType == NoticeBillType.SALES_RETURN) {
            return "退货已确认，正在生成并审核金蝶销售退货单";
        }
        if (auditExistingBill) {
            return "已确认，正在提交并审核金蝶单据";
        }
        return inbound ? "已提交至WMS" : "已出库扣减库存";
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
                    case PRODUCTION_IN -> erpSyncAsyncService.syncPrdInStock(batchNo);
                    case PRODUCTION_RET_STOCK -> erpSyncAsyncService.syncPrdRetStock(batchNo);
                    case PRODUCTION_RETURN -> erpSyncAsyncService.syncReturnMtrl(batchNo, returnCopy);
                    case OUTSOURCE_RETURN -> erpSyncAsyncService.syncSubReturnMtrl(batchNo, subReturnCopy);
                    case PRODUCTION_ISSUE -> erpSyncAsyncService.syncPickMtrl(batchNo, pickCopy);
                    case PRODUCTION_FEED -> erpSyncAsyncService.syncFeedMtrl(batchNo);
                    case OUTSOURCE_ISSUE -> erpSyncAsyncService.syncSubPickMtrl(batchNo, subCopy);
                    case OUTSOURCE_FEED -> erpSyncAsyncService.syncSubFeedMtrl(batchNo);
                    case OTHER_IN, OTHER_OUT, SALES_DELIVERY
                            -> erpSyncAsyncService.syncAuditExistingBill(batchNo);
                    case PURCHASE_RETURN -> erpSyncAsyncService.syncPurMrbActualQty(batchNo);
                    case SALES_RETURN -> erpSyncAsyncService.syncSalReturnStock(batchNo);
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
        } else if (billType == NoticeBillType.PRODUCTION_IN && !recordNos.isEmpty()) {
            Map<String, Object> syncResult = submitBatchService.syncPrdInStockBatch(batchNo);
            applyErpSyncResult(result, batchNo, syncResult, "生产入库单");
        } else if (billType == NoticeBillType.PRODUCTION_RET_STOCK) {
            Map<String, Object> syncResult = submitBatchService.syncPrdRetStockBatch(batchNo);
            applyErpSyncResult(result, batchNo, syncResult, "生产退库单");
        } else if (billType == NoticeBillType.PRODUCTION_RETURN) {
            Map<String, Object> syncResult = submitBatchService.syncReturnMtrlBatch(batchNo, returnMtrlLines);
            applyErpSyncResult(result, batchNo, syncResult, "生产退料单");
        } else if (billType == NoticeBillType.OUTSOURCE_RETURN) {
            Map<String, Object> syncResult = submitBatchService.syncSubReturnMtrlBatch(batchNo, subReturnMtrlLines);
            applyErpSyncResult(result, batchNo, syncResult, "委外退料单");
        } else if (billType == NoticeBillType.PRODUCTION_ISSUE) {
            Map<String, Object> syncResult = submitBatchService.syncPickMtrlBatch(batchNo, pickMtrlLines);
            applyErpSyncResult(result, batchNo, syncResult, "生产领料单");
        } else if (billType == NoticeBillType.PRODUCTION_FEED) {
            Map<String, Object> syncResult = submitBatchService.syncFeedMtrlBatch(batchNo);
            applyErpSyncResult(result, batchNo, syncResult, "生产补料单");
        } else if (billType == NoticeBillType.OUTSOURCE_ISSUE) {
            Map<String, Object> syncResult = submitBatchService.syncSubPickMtrlBatch(batchNo, subPickMtrlLines);
            applyErpSyncResult(result, batchNo, syncResult, "委外领料单");
        } else if (billType == NoticeBillType.OUTSOURCE_FEED) {
            Map<String, Object> syncResult = submitBatchService.syncSubFeedMtrlBatch(batchNo);
            applyErpSyncResult(result, batchNo, syncResult, "委外补料单");
        } else if (billType == NoticeBillType.PURCHASE_RETURN) {
            Map<String, Object> syncResult = submitBatchService.syncPurMrbActualQtyBatch(batchNo);
            applyErpSyncResult(result, batchNo, syncResult, billType.getLabel());
        } else if (billType == NoticeBillType.OTHER_IN
                || billType == NoticeBillType.OTHER_OUT
                || billType == NoticeBillType.SALES_DELIVERY) {
            Map<String, Object> syncResult = submitBatchService.syncAuditExistingBillBatch(batchNo);
            applyErpSyncResult(result, batchNo, syncResult, billType.getLabel());
        } else if (billType == NoticeBillType.SALES_RETURN && !recordNos.isEmpty()) {
            Map<String, Object> syncResult = submitBatchService.syncSalReturnStockBatch(batchNo);
            applyErpSyncResult(result, batchNo, syncResult, "销售退货单");
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
            String formatted = KingdeeResponseMessageFormatter.format(erpMsg);
            result.put("erpSyncMessage", formatted);
            result.put("message", formatted);
            result.put("errorType", "ERP_SYNC_FAILED");
            log.warn("ERP sync failed batchNo={} billLabel={} status={} msg={}",
                    batchNo, billLabel, erpSyncStatus, formatted);
            return;
        }
        String erpBillNo = syncResult.get("erpBillNo") != null
                ? String.valueOf(syncResult.get("erpBillNo")) : null;
        String erpSyncMessage = syncResult.get("erpSyncMessage") != null
                ? String.valueOf(syncResult.get("erpSyncMessage")) : null;
        if (StringUtils.hasText(erpSyncMessage)) {
            result.put("message", erpSyncMessage);
        } else if (StringUtils.hasText(erpBillNo)) {
            result.put("message", "已提交并同步金蝶，" + billLabel + " " + erpBillNo);
        }
    }

    /**
     * 金蝶失败：把扫码行「已处理」还原为提交前快照，可处理=计划−已处理随之恢复。
     * 扫描量保留，便于修改后重提。
     */
    private void restoreSubmittedQtyAfterErpFail(NoticeBillType billType, String billNo,
                                                 Map<Integer, BigDecimal[]> submittedSnapshot) {
        if (billType == null || !StringUtils.hasText(billNo) || submittedSnapshot == null || submittedSnapshot.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, BigDecimal[]> e : submittedSnapshot.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            PdaReceiveScanLine line = lineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                    .eq(PdaReceiveScanLine::getBillType, billType.getCode())
                    .eq(PdaReceiveScanLine::getDirection, billType.getDirection().name())
                    .eq(PdaReceiveScanLine::getBillNo, billNo)
                    .eq(PdaReceiveScanLine::getLineNo, e.getKey()));
            if (line == null) {
                continue;
            }
            BigDecimal prevSubmitted = e.getValue()[0] != null ? e.getValue()[0] : BigDecimal.ZERO;
            BigDecimal prevSubmittedAux = e.getValue().length > 1 && e.getValue()[1] != null
                    ? e.getValue()[1] : BigDecimal.ZERO;
            line.setSubmittedQty(prevSubmitted);
            line.setSubmittedAuxQty(prevSubmittedAux);
            // 扫描量不低于已处理，保留用户已扫待提数量
            if (nullSafe(line.getScannedQty()).compareTo(prevSubmitted) < 0) {
                line.setScannedQty(prevSubmitted);
            }
            if (isMultiUnit(line) && nullSafe(line.getScannedAuxQty()).compareTo(prevSubmittedAux) < 0) {
                line.setScannedAuxQty(prevSubmittedAux);
            }
            line.setUpdateTime(LocalDateTime.now());
            lineMapper.updateById(line);
            log.info("Restore submittedQty after ERP fail billType={} billNo={} line={} submitted={}",
                    billType.getCode(), billNo, e.getKey(), prevSubmitted.stripTrailingZeros().toPlainString());
        }
    }

    /** 金蝶失败：撤销本批次引起的库存变动（入库冲减 / 出库加回） */
    private void undoInventoryAfterErpFail(boolean inbound, boolean auditExistingBill,
                                           List<InventoryChangeCommand> undoCommands) {
        if (auditExistingBill || undoCommands == null || undoCommands.isEmpty()) {
            return;
        }
        for (InventoryChangeCommand cmd : undoCommands) {
            if (cmd == null || cmd.getQuantity() == null
                    || cmd.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            try {
                if (inbound) {
                    inventoryService.decreaseWithTransaction(cmd);
                } else {
                    inventoryService.increaseWithTransaction(cmd);
                }
            } catch (Exception ex) {
                log.error("Undo inventory after ERP fail failed material={} wh={} qty={}",
                        cmd.getMaterialCode(), cmd.getWarehouseCode(), cmd.getQuantity(), ex);
                throw new BusinessException(ErrorCode.CONFLICT,
                        "金蝶同步失败，且库存回滚失败: " + ex.getMessage(),
                        "ERP_SYNC_INVENTORY_ROLLBACK_FAILED");
            }
        }
    }

    /** 金蝶失败：本批次入库记录标记失败并软删，避免计入已处理/成功量 */
    private void markInboundRecordsFailedAndDeleted(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            return;
        }
        List<PdaInboundRecord> records = inboundRecordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSubmitBatchNo, batchNo)
                .eq(PdaInboundRecord::getDeleted, 0));
        LocalDateTime now = LocalDateTime.now();
        for (PdaInboundRecord record : records) {
            record.setErpSyncStatus("FAILED");
            record.setDeleted(1);
            record.setErpSyncTime(now);
            record.setUpdateTime(now);
            if (!StringUtils.hasText(record.getErpSyncMessage())) {
                record.setErpSyncMessage("金蝶同步失败，数量已还原");
            }
            inboundRecordMapper.updateById(record);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo toggleLine(String billNo, Integer lineNo, boolean checked) {
        return toggleLine(NoticeBillType.PURCHASE_RECEIVE, billNo, lineNo, checked);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiveNoticeLineVo toggleLine(NoticeBillType billType, String billNo, Integer lineNo, boolean checked) {
        billLockService.assertHeld(billType.getCode(), billNo);
        PdaReceiveScanLine line = lineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                .eq(PdaReceiveScanLine::getBillType, billType.getCode())
                .eq(PdaReceiveScanLine::getDirection, billType.getDirection().name())
                .eq(PdaReceiveScanLine::getBillNo, billNo)
                .eq(PdaReceiveScanLine::getLineNo, lineNo));
        if (line == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "明细行不存在");
        }
        PdaReceiveScanSession session = requireSession(billType, billNo);
        line.setChecked(checked ? 1 : 0);
        line.setUpdateTime(LocalDateTime.now());
        lineMapper.updateById(line);
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
        billLockService.assertHeld(billType.getCode(), billNo);
        PdaReceiveScanSession session = requireSession(billType, billNo);
        if (isSessionFinished(session.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "单据已提交完成，不可再改数量");
        }
        PdaReceiveScanLine line = lineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                .eq(PdaReceiveScanLine::getBillType, billType.getCode())
                .eq(PdaReceiveScanLine::getDirection, billType.getDirection().name())
                .eq(PdaReceiveScanLine::getBillNo, billNo)
                .eq(PdaReceiveScanLine::getLineNo, lineNo));
        if (line == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "明细行不存在");
        }
        repairDualUnitPending(line);
        if (req.getQty() != null && req.getQty().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "领取数量不能为负");
        }
        if (req.getAuxQty() != null && req.getAuxQty().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "辅助单位数量不能为负");
        }

        BigDecimal submitted = nullSafe(line.getSubmittedQty());
        BigDecimal plan = nullSafe(line.getPlanQty());
        BigDecimal remainStock = plan.subtract(submitted);
        if (remainStock.compareTo(BigDecimal.ZERO) < 0) {
            remainStock = BigDecimal.ZERO;
        }
        BigDecimal submittedAux = nullSafe(line.getSubmittedAuxQty());
        BigDecimal planAux = nullSafe(line.getPlanAuxQty());
        BigDecimal remainAux = planAux.subtract(submittedAux);
        if (remainAux.compareTo(BigDecimal.ZERO) < 0) {
            remainAux = BigDecimal.ZERO;
        }

        BigDecimal stockPending;
        BigDecimal auxPending;
        // 已处理以金蝶已入库为准；可否录入只看可处理余量，不因「已有已处理」锁死
        if (isMultiUnit(line) && isInputMapsToAux(line)) {
            boolean stockFull = plan.compareTo(BigDecimal.ZERO) > 0 && remainStock.compareTo(BigDecimal.ZERO) <= 0;
            boolean auxFull = planAux.compareTo(BigDecimal.ZERO) > 0 && remainAux.compareTo(BigDecimal.ZERO) <= 0;
            if (stockFull || (plan.compareTo(BigDecimal.ZERO) <= 0 && auxFull)) {
                throw new BusinessException(ErrorCode.CONFLICT, "该物料已领满");
            }
            if (req.getAuxQty() != null) {
                auxPending = req.getAuxQty();
            } else if (req.getQty() != null) {
                auxPending = convertByPlanRate(req.getQty(),
                        plan.compareTo(BigDecimal.ZERO) > 0 ? plan : BigDecimal.ONE,
                        planAux.compareTo(BigDecimal.ZERO) > 0 ? planAux : BigDecimal.ONE);
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "领取数量不能为空");
            }
            if (planAux.compareTo(BigDecimal.ZERO) > 0 && auxPending.compareTo(remainAux) > 0) {
                throw qtyExceedsRemainError(line, auxPending, remainAux, line.getAuxUnitCode());
            }
            stockPending = convertByPlanRate(auxPending,
                    planAux.compareTo(BigDecimal.ZERO) > 0 ? planAux : BigDecimal.ONE,
                    plan.compareTo(BigDecimal.ZERO) > 0 ? plan : BigDecimal.ONE);
            if (plan.compareTo(BigDecimal.ZERO) > 0 && stockPending.compareTo(remainStock) > 0) {
                throw qtyExceedsRemainError(line, stockPending, remainStock, line.getUnitCode());
            }
            if (plan.compareTo(BigDecimal.ZERO) <= 0) {
                stockPending = auxPending;
            }
        } else {
            stockPending = req.getQty();
            if (stockPending == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "领取数量不能为空");
            }
            if (plan.compareTo(BigDecimal.ZERO) > 0 && remainStock.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "该物料已领满");
            }
            if (plan.compareTo(BigDecimal.ZERO) > 0 && stockPending.compareTo(remainStock) > 0) {
                throw qtyExceedsRemainError(line, stockPending, remainStock, line.getUnitCode());
            }
            auxPending = resolvePendingAuxQty(line, stockPending, req.getAuxQty());
        }

        line.setScannedQty(submitted.add(stockPending));
        line.setScannedAuxQty(submittedAux.add(auxPending));
        line.setChecked(1);
        if (!StringUtils.hasText(line.getScannedBarcode())) {
            line.setScannedBarcode("MANUAL");
        }
        line.setUpdateTime(LocalDateTime.now());
        lineMapper.updateById(line);
        markQtyChanged(billType, billNo, session);
        return toLineVo(line);
    }

    /**
     * 手工录入数量超出可处理余量：直接提示，不再静默截断成余量。
     */
    private BusinessException qtyExceedsRemainError(PdaReceiveScanLine line, BigDecimal input,
                                                    BigDecimal remain, String unitCode) {
        String unit = StringUtils.hasText(unitCode) ? unitCode.trim() : "";
        return new BusinessException(ErrorCode.BAD_REQUEST,
                String.format("「%s」本次数量 %s%s 超过可处理数量 %s%s，请修改后重试",
                        firstNonBlank(line.getMaterialName(), line.getMaterialCode()),
                        plainQty(input), unit,
                        plainQty(remain), unit));
    }

    private static String plainQty(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private void applyAuxUnitFromKingdee(PdaReceiveScanLine line, KingdeeReceiveBillLineVo kdLine) {
        if (line == null || kdLine == null) {
            return;
        }
        String auxUnit = kdLine.getPriceUnitCode();
        BigDecimal planAux = kdLine.getPriceUnitQty();
        // 只要金蝶返回了库存单位 + 计价单位（无论是否相同）即启用双单位编辑
        boolean multi = isMultiUnit(kdLine.getUnitCode(), auxUnit);
        if (!multi) {
            line.setAuxUnitCode(null);
            line.setPlanAuxQty(null);
            if (line.getScannedAuxQty() == null) {
                line.setScannedAuxQty(BigDecimal.ZERO);
            }
            if (line.getSubmittedAuxQty() == null) {
                line.setSubmittedAuxQty(BigDecimal.ZERO);
            }
            return;
        }
        line.setAuxUnitCode(auxUnit.trim());
        // 计价数量缺失时：同单位可回落库存计划；异单位（如 PCS/KG）禁止 1:1，否则换算率错误
        if (planAux == null || planAux.compareTo(BigDecimal.ZERO) <= 0) {
            if (sameUnitCode(kdLine.getUnitCode(), auxUnit)) {
                planAux = resolvePlanQty(kdLine);
            } else {
                log.warn("双单位缺计价数量，无法换算 bill={} material={} stockUnit={} priceUnit={}",
                        line.getBillNo(), line.getMaterialCode(), kdLine.getUnitCode(), auxUnit);
                line.setAuxUnitCode(null);
                line.setPlanAuxQty(null);
                return;
            }
        }
        line.setPlanAuxQty(planAux);
        if (line.getScannedAuxQty() == null) {
            line.setScannedAuxQty(BigDecimal.ZERO);
        }
        if (line.getSubmittedAuxQty() == null) {
            line.setSubmittedAuxQty(BigDecimal.ZERO);
        }
    }

    /**
     * 金蝶同时返回库存单位（FUnitID）与计价单位（FPriceUnitId）即视为双单位，
     * 两单位相同或不同均展示双单位编辑。
     */
    private boolean isMultiUnit(String unitCode, String auxUnitCode) {
        return StringUtils.hasText(unitCode) && StringUtils.hasText(auxUnitCode);
    }

    private boolean sameUnitCode(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean isMultiUnit(PdaReceiveScanLine line) {
        return line != null && isMultiUnit(line.getUnitCode(), line.getAuxUnitCode());
    }

    /**
     * 主录件数、换算 KG：
     * - 库存=KG、计价=PCS → 主录计价，换算库存 KG
     * - 库存=PCS、计价=KG → 主录库存，换算计价 KG
     * - 均非重量 → 默认主录计价，换算库存
     */
    private boolean isInputMapsToAux(PdaReceiveScanLine line) {
        if (!isMultiUnit(line)) {
            return false;
        }
        boolean stockIsWeight = isWeightUnit(line.getUnitCode());
        boolean priceIsWeight = isWeightUnit(line.getAuxUnitCode());
        if (stockIsWeight && !priceIsWeight) {
            return true;
        }
        if (!stockIsWeight && priceIsWeight) {
            return false;
        }
        return true;
    }

    private boolean isWeightUnit(String unitCode) {
        if (!StringUtils.hasText(unitCode)) {
            return false;
        }
        String raw = unitCode.trim();
        String u = raw.toUpperCase();
        return "KG".equals(u)
                || "KGS".equals(u)
                || "KILOGRAM".equals(u)
                || "千克".equals(raw)
                || "公斤".equals(raw)
                || "G".equals(u)
                || "克".equals(raw)
                || "T".equals(u)
                || "吨".equals(raw)
                || "斤".equals(raw);
    }

    /**
     * 解析本次计价数量：优先用手输值；否则按计划换算率由库存数量推算。
     */
    private BigDecimal resolvePendingAuxQty(PdaReceiveScanLine line, BigDecimal pendingQty, BigDecimal requestedAuxQty) {
        if (!isMultiUnit(line)) {
            return BigDecimal.ZERO;
        }
        BigDecimal submittedAux = nullSafe(line.getSubmittedAuxQty());
        BigDecimal planAux = nullSafe(line.getPlanAuxQty());
        BigDecimal remainAux = planAux.subtract(submittedAux);
        if (remainAux.compareTo(BigDecimal.ZERO) < 0) {
            remainAux = BigDecimal.ZERO;
        }
        BigDecimal auxPending;
        if (requestedAuxQty != null) {
            if (requestedAuxQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "辅助单位数量不能为负");
            }
            auxPending = requestedAuxQty;
        } else {
            auxPending = convertByPlanRate(pendingQty, line.getPlanQty(), line.getPlanAuxQty());
        }
        if (auxPending.compareTo(remainAux) > 0) {
            auxPending = remainAux;
        }
        return auxPending;
    }

    private BigDecimal convertByPlanRate(BigDecimal qty, BigDecimal planQty, BigDecimal planAuxQty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal plan = nullSafe(planQty);
        BigDecimal planAux = nullSafe(planAuxQty);
        if (plan.compareTo(BigDecimal.ZERO) <= 0 || planAux.compareTo(BigDecimal.ZERO) <= 0) {
            return qty;
        }
        return qty.multiply(planAux).divide(plan, 6, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
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
            session.setWarehouseCode(resolveSessionWarehouseCode(bill));
            // 仅打开明细不视为扫码中；数量变更后再置为 SCANNING
            session.setStatus("NEW");
            session.setTotalLines(bill.getLines() != null ? bill.getLines().size() : 0);
            session.setCheckedLines(0);
            session.setSubmittedLines(0);
            session.setOperatorId(String.valueOf(user.getUserId()));
            session.setOperatorName(user.getRealName() != null ? user.getRealName() : user.getUsername());
            copyCreatorMeta(session, bill);
            session.setCreateTime(LocalDateTime.now());
            sessionMapper.insert(session);
        } else {
            boolean changed = copyCreatorMeta(session, bill);
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

    private boolean copyCreatorMeta(PdaReceiveScanSession session, KingdeeReceiveBillVo bill) {
        if (session == null || bill == null) {
            return false;
        }
        boolean changed = false;
        if (StringUtils.hasText(bill.getCreatorKdUserNumber())
                && !bill.getCreatorKdUserNumber().trim().equals(session.getCreatorKdUserNumber())) {
            session.setCreatorKdUserNumber(bill.getCreatorKdUserNumber().trim());
            changed = true;
        }
        if (StringUtils.hasText(bill.getCreatorName())
                && !bill.getCreatorName().trim().equals(session.getCreatorKdUserName())) {
            session.setCreatorKdUserName(bill.getCreatorName().trim());
            changed = true;
        }
        return changed;
    }

    /** 会话仓库存 WMS 编码；金蝶占位仓（CK004 未分配）不作为会话仓。 */
    private String resolveSessionWarehouseCode(KingdeeReceiveBillVo bill) {
        String raw = bill != null ? bill.getWarehouseCode() : null;
        if (erpWarehouseResolver.isUnassigned(raw) && bill != null && bill.getLines() != null) {
            raw = bill.getLines().stream()
                    .map(KingdeeReceiveBillLineVo::getStockWarehouseCode)
                    .filter(code -> !erpWarehouseResolver.isUnassigned(code))
                    .findFirst()
                    .orElse(null);
        }
        if (erpWarehouseResolver.isUnassigned(raw)) {
            return "WH01";
        }
        String wms = erpWarehouseResolver.resolveWmsCode(raw);
        return StringUtils.hasText(wms) ? wms : "WH01";
    }

    private void syncLinesFromKingdee(PdaReceiveScanSession session, KingdeeReceiveBillVo bill) {
        if (bill.getLines() == null) {
            return;
        }
        Map<String, String> autoWarehouseCache = new HashMap<>();
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
                applyAuxUnitFromKingdee(line, kdLine);
                refreshPlanQtyFromKingdee(line, kdLine);
                line.setErpStockCode(resolveAutoWarehouse(kdLine, kdLine.getStockWarehouseCode(), autoWarehouseCache));
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
                existing.setUnitCode(kdLine.getUnitCode());
                applyAuxUnitFromKingdee(existing, kdLine);
                String resolvedStock = resolveAutoWarehouse(kdLine, kdLine.getStockWarehouseCode(), autoWarehouseCache);
                if (StringUtils.hasText(resolvedStock)) {
                    existing.setErpStockCode(resolvedStock);
                } else if (erpWarehouseResolver.isUnassigned(existing.getErpStockCode())) {
                    existing.setErpStockCode(null);
                }
                // 按金蝶已入库量回写「已处理」，并刷新计划/可处理
                refreshPlanQtyFromKingdee(existing, kdLine);
                existing.setUpdateTime(LocalDateTime.now());
                lineMapper.updateById(existing);
            }
        }
        session.setTotalLines(bill.getLines().size());
        refreshSessionCounters(session);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /**
     * 金蝶有已入库量(FInStockJoinBaseQty)时，「已处理」写为该数量；
     * 「计划」固定为金蝶应收/合格数，禁止用「已处理+剩余」叠加。
     * <p>收料通知单用合格入库关联量；生产汇报用合格品入库选单量（FStockInSelQty）。
     * <p>本地已处理大于金蝶时：仅存在 PENDING/SYNCING 同步才短暂保留；否则压回金蝶已入库
     * （避免下推失败的数量一直占着已处理、并推高计划）。
     */
    private void refreshPlanQtyFromKingdee(PdaReceiveScanLine line, KingdeeReceiveBillLineVo kdLine) {
        if (line == null || kdLine == null) {
            return;
        }
        if (!usesKingdeeInStockJoin(line.getBillType())) {
            refreshPlanQtyWithoutInStockJoin(line, kdLine);
            return;
        }
        BigDecimal erpJoined = BigDecimal.ZERO;
        if (kdLine.getInStockJoinBaseQty() != null) {
            erpJoined = kdLine.getInStockJoinBaseQty();
            if (erpJoined.compareTo(BigDecimal.ZERO) < 0) {
                erpJoined = BigDecimal.ZERO;
            }
        }

        BigDecimal basePlan = resolvePlanQty(kdLine);
        if (basePlan.compareTo(BigDecimal.ZERO) <= 0 && erpJoined.compareTo(BigDecimal.ZERO) > 0) {
            basePlan = erpJoined;
        }
        // 计划=金蝶应收，不再做 submitted + remain 叠加
        line.setPlanQty(basePlan);

        BigDecimal wmsSubmitted = nullSafe(line.getSubmittedQty());
        BigDecimal successSum = sumSuccessfulInboundQty(line);
        // 已处理 = 金蝶已入库 ∪ 本地已同步成功；失败批次不计入
        BigDecimal nextSubmitted = erpJoined.max(successSum);
        if (wmsSubmitted.compareTo(nextSubmitted) > 0 && hasInFlightErpSync(line)) {
            // 刚提交尚未回写金蝶：短暂保留本地，避免可处理闪回导致重复提交
            nextSubmitted = wmsSubmitted;
        } else if (wmsSubmitted.compareTo(nextSubmitted) != 0) {
            log.info("Sync submittedQty from Kingdee/success records: bill={} line={} material={} wms={} erp={} successSum={}",
                    line.getBillNo(), line.getLineNo(), line.getMaterialCode(),
                    wmsSubmitted.stripTrailingZeros().toPlainString(),
                    erpJoined.stripTrailingZeros().toPlainString(),
                    successSum.stripTrailingZeros().toPlainString());
        }
        line.setSubmittedQty(nextSubmitted);
        wmsSubmitted = nextSubmitted;
        if (erpJoined.compareTo(BigDecimal.ZERO) <= 0
                && nullSafe(line.getScannedQty()).compareTo(BigDecimal.ZERO) <= 0) {
            line.setChecked(0);
        }

        BigDecimal scanned = nullSafe(line.getScannedQty());
        if (scanned.compareTo(wmsSubmitted) < 0) {
            line.setScannedQty(wmsSubmitted);
        } else if (wmsSubmitted.compareTo(BigDecimal.ZERO) > 0 && scanned.compareTo(basePlan) > 0) {
            line.setScannedQty(basePlan);
        }

        if (!isMultiUnit(line)) {
            return;
        }
        BigDecimal basePlanAux = kdLine.getPriceUnitQty();
        if (basePlanAux == null || basePlanAux.compareTo(BigDecimal.ZERO) <= 0) {
            basePlanAux = basePlan.compareTo(BigDecimal.ZERO) > 0 ? basePlan : nullSafe(line.getPlanAuxQty());
        }
        line.setPlanAuxQty(basePlanAux);

        BigDecimal submittedAux = nullSafe(line.getSubmittedAuxQty());
        BigDecimal successAuxSum = sumSuccessfulInboundAuxQty(line);
        BigDecimal planForRate = resolvePlanQty(kdLine);
        if (planForRate.compareTo(BigDecimal.ZERO) <= 0) {
            planForRate = basePlan;
        }
        if (!hasInFlightErpSync(line)
                && planForRate.compareTo(BigDecimal.ZERO) > 0
                && basePlanAux.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal fromStock = convertByPlanRate(wmsSubmitted, planForRate, basePlanAux);
            submittedAux = fromStock.max(successAuxSum);
            line.setSubmittedAuxQty(submittedAux);
        } else if (submittedAux.compareTo(basePlanAux) > 0) {
            submittedAux = basePlanAux;
            line.setSubmittedAuxQty(submittedAux);
        }

        BigDecimal scannedAux = nullSafe(line.getScannedAuxQty());
        if (scannedAux.compareTo(submittedAux) < 0) {
            line.setScannedAuxQty(submittedAux);
        } else if (submittedAux.compareTo(BigDecimal.ZERO) > 0 && scannedAux.compareTo(basePlanAux) > 0) {
            line.setScannedAuxQty(basePlanAux);
        }
    }

    /** 该行是否存在尚未结束的金蝶入库同步（失败不算在途）。 */
    /**
     * 该单据是否有刚提交、金蝶尚未回写完成的批次（出库单据无入库记录，按批次判断）。
     * <p>限定最近 {@value #IN_FLIGHT_BATCH_MINUTES} 分钟，避免异步同步异常中断的批次长期卡住状态回退。
     */
    private boolean hasInFlightSubmitBatch(PdaReceiveScanLine line) {
        if (line == null) {
            return false;
        }
        return hasInFlightSubmitBatch(line.getBillType(), line.getBillNo());
    }

    private boolean hasInFlightSubmitBatch(NoticeBillType billType, String billNo) {
        return billType != null && hasInFlightSubmitBatch(billType.getCode(), billNo);
    }

    private boolean hasInFlightSubmitBatch(String billTypeCode, String billNo) {
        if (!StringUtils.hasText(billTypeCode) || !StringUtils.hasText(billNo)) {
            return false;
        }
        Long count = submitBatchMapper.selectCount(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBillType, billTypeCode)
                .eq(PdaReceiveSubmitBatch::getBillNo, billNo)
                .eq(PdaReceiveSubmitBatch::getErpSyncStatus, "PENDING")
                .ge(PdaReceiveSubmitBatch::getSubmitTime,
                        LocalDateTime.now().minusMinutes(IN_FLIGHT_BATCH_MINUTES)));
        return count != null && count > 0;
    }

    private boolean hasInFlightErpSync(PdaReceiveScanLine line) {
        if (line == null || line.getLineNo() == null || !StringUtils.hasText(line.getBillNo())) {
            return false;
        }
        Long count = inboundRecordMapper.selectCount(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSourceBillNo, line.getBillNo())
                .eq(PdaInboundRecord::getSourceLineNo, line.getLineNo())
                .in(PdaInboundRecord::getErpSyncStatus, "PENDING", "SYNCING")
                .eq(PdaInboundRecord::getDeleted, 0));
        return count != null && count > 0;
    }

    /** 已成功同步到金蝶的入库数量合计（失败批次不计入已处理）。 */
    private BigDecimal sumSuccessfulInboundQty(PdaReceiveScanLine line) {
        if (line == null || line.getLineNo() == null || !StringUtils.hasText(line.getBillNo())) {
            return BigDecimal.ZERO;
        }
        List<PdaInboundRecord> rows = inboundRecordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSourceBillNo, line.getBillNo())
                .eq(PdaInboundRecord::getSourceLineNo, line.getLineNo())
                .eq(PdaInboundRecord::getErpSyncStatus, "SUCCESS")
                .eq(PdaInboundRecord::getDeleted, 0));
        BigDecimal sum = BigDecimal.ZERO;
        for (PdaInboundRecord row : rows) {
            if (row.getQuantity() != null) {
                sum = sum.add(row.getQuantity());
            }
        }
        return sum;
    }

    private BigDecimal sumSuccessfulInboundAuxQty(PdaReceiveScanLine line) {
        if (line == null || line.getLineNo() == null || !StringUtils.hasText(line.getBillNo())) {
            return BigDecimal.ZERO;
        }
        List<PdaInboundRecord> rows = inboundRecordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSourceBillNo, line.getBillNo())
                .eq(PdaInboundRecord::getSourceLineNo, line.getLineNo())
                .eq(PdaInboundRecord::getErpSyncStatus, "SUCCESS")
                .eq(PdaInboundRecord::getDeleted, 0));
        BigDecimal sum = BigDecimal.ZERO;
        for (PdaInboundRecord row : rows) {
            if (row.getAuxQuantity() != null) {
                sum = sum.add(row.getAuxQuantity());
            }
        }
        return sum;
    }

    /**
     * 收料通知单才用金蝶「合格入库关联量」回写已处理；采购退料/领退料等实退(发)数量不是已处理。
     */
    private boolean usesKingdeeInStockJoin(String billTypeCode) {
        return NoticeBillType.PURCHASE_RECEIVE.getCode().equals(billTypeCode)
                || NoticeBillType.PRODUCTION_IN.getCode().equals(billTypeCode);
    }

    /**
     * 领料/补料单的金蝶实发数量 FActualQty 代表已领进度（解析在 inStockJoinBaseQty）。
     */
    private boolean usesKingdeeActualQtyProgress(String billTypeCode) {
        return NoticeBillType.PRODUCTION_ISSUE.getCode().equals(billTypeCode)
                || NoticeBillType.PRODUCTION_FEED.getCode().equals(billTypeCode)
                || NoticeBillType.OUTSOURCE_ISSUE.getCode().equals(billTypeCode)
                || NoticeBillType.OUTSOURCE_FEED.getCode().equals(billTypeCode);
    }

    /**
     * 金蝶实发数量小于本地已领时（单据被反审核或改量），已领进度回退到金蝶口径，
     * 否则反审核后的单据会一直停留在「已全部领取」且无法再扫。
     * <p>刚提交尚未回写金蝶时（存在进行中的 ERP 同步）不回退，避免可处理量闪回导致重复提交。
     *
     * @return 回退后的已处理数量
     */
    private BigDecimal rollbackSubmittedToErpActualQty(PdaReceiveScanLine line, KingdeeReceiveBillLineVo kdLine,
                                                       BigDecimal submitted) {
        if (!usesKingdeeActualQtyProgress(line.getBillType()) || kdLine.getInStockJoinBaseQty() == null) {
            return submitted;
        }
        BigDecimal erpActual = kdLine.getInStockJoinBaseQty().max(BigDecimal.ZERO);
        if (submitted.compareTo(erpActual) <= 0 || hasInFlightSubmitBatch(line)) {
            return submitted;
        }
        log.info("Rollback submittedQty to Kingdee actual qty: billType={} bill={} line={} material={} wms={} erp={}",
                line.getBillType(), line.getBillNo(), line.getLineNo(), line.getMaterialCode(),
                plainQty(submitted), plainQty(erpActual));
        line.setSubmittedQty(erpActual);
        if (erpActual.compareTo(BigDecimal.ZERO) <= 0) {
            line.setChecked(0);
            line.setSubmittedAuxQty(BigDecimal.ZERO);
            line.setScannedAuxQty(BigDecimal.ZERO);
        }
        return erpActual;
    }

    /**
     * 非收料入库单据：计划=金蝶实退/实发等数量；已处理仅保留本地扫码提交，并纠偏误把计划写成已处理的脏数据。
     */
    private void refreshPlanQtyWithoutInStockJoin(PdaReceiveScanLine line, KingdeeReceiveBillLineVo kdLine) {
        BigDecimal erpPlan = resolvePlanQty(kdLine);
        BigDecimal localPlan = nullSafe(line.getPlanQty());
        BigDecimal submitted = nullSafe(line.getSubmittedQty());
        // 分批回写后金蝶实退会变小/变大；已开始提交时不要把本地计划压成当前实退，否则无法继续扫第二批
        BigDecimal plan = erpPlan;
        if (submitted.compareTo(BigDecimal.ZERO) > 0 && localPlan.compareTo(erpPlan) > 0) {
            plan = localPlan;
        }
        line.setPlanQty(plan);
        submitted = rollbackSubmittedToErpActualQty(line, kdLine, submitted);

        BigDecimal scanned = nullSafe(line.getScannedQty());
        // 尚未扫码却「已处理=计划」：上次误把 FRMREALQTY 当 FInStockJoin，清零以便可录入
        if (scanned.compareTo(BigDecimal.ZERO) <= 0
                && submitted.compareTo(BigDecimal.ZERO) > 0
                && submitted.compareTo(plan) == 0) {
            log.info("Reset false submittedQty for non-receive bill: billType={} bill={} line={} material={} qty={}",
                    line.getBillType(), line.getBillNo(), line.getLineNo(), line.getMaterialCode(),
                    submitted.stripTrailingZeros().toPlainString());
            submitted = BigDecimal.ZERO;
            line.setSubmittedQty(submitted);
            line.setChecked(0);
        }
        if (scanned.compareTo(submitted) < 0) {
            line.setScannedQty(submitted);
        } else if (scanned.compareTo(plan) > 0) {
            line.setScannedQty(plan);
        }

        if (!isMultiUnit(line)) {
            return;
        }
        BigDecimal planAux = kdLine.getPriceUnitQty();
        if (planAux == null || planAux.compareTo(BigDecimal.ZERO) <= 0) {
            if (sameUnitCode(line.getUnitCode(), line.getAuxUnitCode())) {
                planAux = plan;
            } else if (isMultiUnit(line)) {
                // 保留原 planAux，避免异单位被重置成 1:1
                planAux = nullSafe(line.getPlanAuxQty());
                if (planAux.compareTo(BigDecimal.ZERO) <= 0) {
                    return;
                }
            } else {
                return;
            }
        }
        line.setPlanAuxQty(planAux);
        BigDecimal submittedAux = nullSafe(line.getSubmittedAuxQty());
        BigDecimal scannedAux = nullSafe(line.getScannedAuxQty());
        if (scannedAux.compareTo(BigDecimal.ZERO) <= 0
                && submittedAux.compareTo(BigDecimal.ZERO) > 0
                && submittedAux.compareTo(planAux) == 0) {
            submittedAux = BigDecimal.ZERO;
            line.setSubmittedAuxQty(submittedAux);
        }
        if (scannedAux.compareTo(submittedAux) < 0) {
            line.setScannedAuxQty(submittedAux);
        } else if (scannedAux.compareTo(planAux) > 0) {
            line.setScannedAuxQty(planAux);
        }
    }

    private boolean hasPendingSubmitQty(PdaReceiveScanLine line) {
        if (line == null) {
            return false;
        }
        BigDecimal pending = nullSafe(line.getScannedQty()).subtract(nullSafe(line.getSubmittedQty()));
        if (pending.compareTo(BigDecimal.ZERO) > 0) {
            return true;
        }
        if (!isMultiUnit(line)) {
            return false;
        }
        BigDecimal pendingAux = nullSafe(line.getScannedAuxQty()).subtract(nullSafe(line.getSubmittedAuxQty()));
        return pendingAux.compareTo(BigDecimal.ZERO) > 0;
    }

    private void refreshSessionCounters(PdaReceiveScanSession session) {
        List<PdaReceiveScanLine> lines = loadLines(
                NoticeBillType.fromCode(session.getBillType()), session.getBillNo());
        refreshSessionCounters(session, lines);
    }

    private void refreshSessionCounters(PdaReceiveScanSession session, List<PdaReceiveScanLine> lines) {
        if (lines == null) {
            lines = List.of();
        }
        int checked = (int) lines.stream().filter(l -> l.getChecked() != null && l.getChecked() == 1).count();
        int submitted = (int) lines.stream()
                .filter(l -> nullSafe(l.getSubmittedQty()).compareTo(nullSafe(l.getPlanQty())) >= 0
                        && nullSafe(l.getPlanQty()).compareTo(BigDecimal.ZERO) > 0)
                .count();
        session.setCheckedLines(checked);
        session.setSubmittedLines(submitted);
        session.setTotalLines(lines.size());
        boolean hasSubmittedQty = lines.stream()
                .anyMatch(l -> nullSafe(l.getSubmittedQty()).compareTo(BigDecimal.ZERO) > 0
                        || nullSafe(l.getSubmittedAuxQty()).compareTo(BigDecimal.ZERO) > 0);
        boolean hasPendingQty = lines.stream().anyMatch(l -> {
            BigDecimal pending = nullSafe(l.getScannedQty()).subtract(nullSafe(l.getSubmittedQty()));
            BigDecimal pendingAux = nullSafe(l.getScannedAuxQty()).subtract(nullSafe(l.getSubmittedAuxQty()));
            return pending.compareTo(BigDecimal.ZERO) > 0 || pendingAux.compareTo(BigDecimal.ZERO) > 0;
        });
        boolean noProcessableRemain = !lines.isEmpty()
                && lines.stream().allMatch(this::isLineFullyProcessed);
        // 可处理全 0 → 已完成（列表不展示）
        if (noProcessableRemain || (submitted > 0 && submitted >= lines.size())) {
            for (PdaReceiveScanLine line : lines) {
                clampPendingToSubmitted(line);
            }
            session.setStatus("COMPLETED");
        } else if (hasPendingQty || hasSubmittedQty) {
            session.setStatus("SCANNING");
        } else {
            session.setStatus("NEW");
        }
    }

    /** 可处理为 0 时将 scanned 压到 submitted，清除无法提交的草稿 */
    private void clampPendingToSubmitted(PdaReceiveScanLine line) {
        if (line == null || hasProcessableRemain(line)) {
            return;
        }
        BigDecimal submitted = nullSafe(line.getSubmittedQty());
        BigDecimal submittedAux = nullSafe(line.getSubmittedAuxQty());
        boolean changed = false;
        if (nullSafe(line.getScannedQty()).compareTo(submitted) != 0) {
            line.setScannedQty(submitted);
            changed = true;
        }
        if (nullSafe(line.getScannedAuxQty()).compareTo(submittedAux) != 0) {
            line.setScannedAuxQty(submittedAux);
            changed = true;
        }
        if (changed) {
            line.setUpdateTime(LocalDateTime.now());
            lineMapper.updateById(line);
        }
    }

    /** 行是否仍有可处理数量（计划-已提交；双单位任一侧有余量即视为可处理） */
    private boolean hasProcessableRemain(PdaReceiveScanLine line) {
        if (line == null) {
            return false;
        }
        BigDecimal plan = nullSafe(line.getPlanQty());
        BigDecimal submitted = nullSafe(line.getSubmittedQty());
        BigDecimal remain = plan.subtract(submitted);
        if (remain.compareTo(BigDecimal.ZERO) > 0) {
            return true;
        }
        if (isMultiUnit(line)) {
            BigDecimal remainAux = nullSafe(line.getPlanAuxQty()).subtract(nullSafe(line.getSubmittedAuxQty()));
            if (remainAux.compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否明确已处理满：有计划或已处理量，且可处理余量为 0。
     * 计划与已处理均为 0 时不算完结（避免未同步明细被误剔出列表）。
     */
    private boolean isLineFullyProcessed(PdaReceiveScanLine line) {
        if (line == null) {
            return false;
        }
        BigDecimal plan = nullSafe(line.getPlanQty());
        BigDecimal submitted = nullSafe(line.getSubmittedQty());
        BigDecimal planAux = nullSafe(line.getPlanAuxQty());
        BigDecimal submittedAux = nullSafe(line.getSubmittedAuxQty());
        boolean hasQtyBasis = plan.compareTo(BigDecimal.ZERO) > 0
                || submitted.compareTo(BigDecimal.ZERO) > 0
                || planAux.compareTo(BigDecimal.ZERO) > 0
                || submittedAux.compareTo(BigDecimal.ZERO) > 0;
        return hasQtyBasis && !hasProcessableRemain(line);
    }

    /**
     * 金蝶列表仍能查到时重开本地已完结会话：
     * 提交即审核（反审核回到未审核）、销售发货（仍有未出库数量）。
     * <p>刚提交、金蝶尚未回写完成的批次不重开，避免同步窗口内单据闪回可扫状态导致重复操作。
     *
     * @return 重开后的会话；无需重开时原样返回
     */
    private PdaReceiveScanSession reopenSessionForUnauditedBill(NoticeBillType billType,
                                                                PdaReceiveScanSession session) {
        if (billType == null
                || (!billType.isSubmitThenAuditBill() && !billType.isOpenRemainOutQtyListBill())
                || session == null
                || !isSessionFinished(session.getStatus())
                || hasInFlightSubmitBatch(billType, session.getBillNo())) {
            return session;
        }
        List<PdaReceiveScanLine> lines = loadLines(billType, session.getBillNo());
        for (PdaReceiveScanLine line : lines) {
            line.setScannedQty(BigDecimal.ZERO);
            line.setScannedAuxQty(BigDecimal.ZERO);
            line.setSubmittedQty(BigDecimal.ZERO);
            line.setSubmittedAuxQty(BigDecimal.ZERO);
            line.setChecked(0);
            line.setUpdateTime(LocalDateTime.now());
            lineMapper.updateById(line);
        }
        log.info("Reopen finished session for unaudited bill: billType={} billNo={} lines={}",
                billType.getCode(), session.getBillNo(), lines.size());
        session.setStatus("NEW");
        session.setCheckedLines(0);
        session.setSubmittedLines(0);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return session;
    }

    private void reconcileSessionStatuses(Collection<PdaReceiveScanSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (PdaReceiveScanSession session : sessions) {
            reconcileSessionStatus(session, null);
        }
    }

    /**
     * 短缓存列表再按会话状态过滤，避免完结后仍短暂出现在列表。
     */
    private List<ReceiveNoticeListItemVo> filterVisibleByLatestSession(
            NoticeBillType billType, List<ReceiveNoticeListItemVo> visible) {
        if (visible == null || visible.isEmpty()) {
            return visible == null ? List.of() : visible;
        }
        List<String> billNos = visible.stream()
                .map(ReceiveNoticeListItemVo::getBillNo)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        Map<String, PdaReceiveScanSession> sessionMap = loadSessions(billType, billNos);
        reconcileSessionStatuses(sessionMap.values());
        return visible.stream()
                .filter(v -> {
                    if (v == null || !StringUtils.hasText(v.getBillNo())) {
                        return false;
                    }
                    PdaReceiveScanSession session = sessionMap.get(v.getBillNo().trim());
                    if (billType != null && billType.isOpenRemainOutQtyListBill()) {
                        return true;
                    }
                    if (session == null) {
                        return !isSessionFinished(v.getScanStatus());
                    }
                    return !isSessionFinished(session.getStatus());
                })
                .collect(Collectors.toList());
    }

    /**
     * 按行数量重算状态：有提交量或可处理全 0 → 已完成；仅有待提交量 → 扫码中。
     */
    private void reconcileSessionStatus(PdaReceiveScanSession session, List<PdaReceiveScanLine> lines) {
        if (session == null) {
            return;
        }
        String before = session.getStatus();
        if (lines == null) {
            refreshSessionCounters(session);
        } else {
            refreshSessionCounters(session, lines);
        }
        if (!Objects.equals(before, session.getStatus())) {
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
            NoticeBillType billType = NoticeBillType.fromCode(session.getBillType());
            if (billType != null) {
                evictListCaches(billType);
            }
        }
    }

    /** 已完结会话：含历史 PARTIAL_SUBMITTED，列表不再展示 */
    private static boolean isSessionFinished(String status) {
        return "COMPLETED".equals(status) || "PARTIAL_SUBMITTED".equals(status);
    }

    /**
     * 金蝶已按未出库数量过滤的单据必须展示；本地会话完结不能当成已出完而藏掉。
     */
    private static boolean keepKingdeeListedBill(NoticeBillType billType, ReceiveNoticeListItemVo vo) {
        if (vo == null) {
            return false;
        }
        if (billType != null && billType.isOpenRemainOutQtyListBill()) {
            return true;
        }
        return !isSessionFinished(vo.getScanStatus());
    }

    /** 数量变更后失效列表缓存，保证列表及时显示「扫码中」 */
    private void markQtyChanged(NoticeBillType billType, String billNo, PdaReceiveScanSession session) {
        refreshSessionCounters(session);
        // 仅未提交完结时：待收料 → 扫码中
        if ("NEW".equals(session.getStatus())) {
            session.setStatus("SCANNING");
        }
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        evictListCaches(billType);
    }

    private void evictListCaches(NoticeBillType billType) {
        if (billType == null) {
            return;
        }
        pdaShortCache.evictByPrefix("list:" + billType.getCode() + ":");
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
        String lineErpStock = lines == null ? null : lines.stream()
                .map(PdaReceiveScanLine::getErpStockCode)
                .filter(code -> !erpWarehouseResolver.isUnassigned(code))
                .findFirst()
                .orElse(null);
        vo.setErpWarehouseCode(lineErpStock);
        vo.setScanStatus(session != null ? session.getStatus() : "NEW");
        vo.setCreatorName(session != null && StringUtils.hasText(session.getCreatorKdUserName())
                ? session.getCreatorKdUserName()
                : (bill != null ? bill.getCreatorName() : null));
        vo.setCreatorKdUserNumber(session != null && StringUtils.hasText(session.getCreatorKdUserNumber())
                ? session.getCreatorKdUserNumber()
                : (bill != null ? bill.getCreatorKdUserNumber() : null));
        vo.setLockRequired(true);
        vo.setTotalLines(session != null ? session.getTotalLines() : lines.size());
        vo.setCheckedLines(session != null ? session.getCheckedLines() : 0);
        vo.setSubmittedLines(session != null ? session.getSubmittedLines() : 0);
        if (lines != null) {
            for (PdaReceiveScanLine line : lines) {
                repairDualUnitPending(line);
            }
        }
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

    private void enrichLockInfo(NoticeBillType billType, List<ReceiveNoticeListItemVo> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<String> billNos = items.stream()
                .map(ReceiveNoticeListItemVo::getBillNo)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        Map<String, PdaBillLock> locks = billLockService.findActiveLocks(billType.getCode(), billNos);
        for (ReceiveNoticeListItemVo item : items) {
            if (item == null || !StringUtils.hasText(item.getBillNo())) {
                continue;
            }
            PdaBillLock lock = locks.get(item.getBillNo().trim());
            if (lock != null) {
                item.setLocked(true);
                item.setLockUserName(lock.getLockUserName());
            } else {
                item.setLocked(false);
                item.setLockUserName(null);
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
            vo.setCreatorName(StringUtils.hasText(session.getCreatorKdUserName())
                    ? session.getCreatorKdUserName()
                    : bill.getCreatorName());
            vo.setTotalLines(resolveTotalLines(bill, session));
            vo.setCheckedLines(session.getCheckedLines());
            vo.setSubmittedLines(session.getSubmittedLines());
            vo.setInProgress("SCANNING".equals(session.getStatus()));
            vo.setPendingLines(Math.max(0, vo.getTotalLines() - session.getSubmittedLines()));
        } else {
            vo.setScanStatus("NEW");
            vo.setCreatorName(bill.getCreatorName());
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

    /** 扫码中双单位待提交量两侧对齐并落库，保证重新进入后可编辑展示 */
    private boolean repairDualUnitPending(PdaReceiveScanLine line) {
        if (line == null || !isMultiUnit(line)) {
            return false;
        }
        BigDecimal submitted = nullSafe(line.getSubmittedQty());
        BigDecimal scanned = nullSafe(line.getScannedQty());
        if (scanned.compareTo(submitted) < 0) {
            scanned = submitted;
        }
        BigDecimal pending = scanned.subtract(submitted);
        BigDecimal submittedAux = nullSafe(line.getSubmittedAuxQty());
        BigDecimal scannedAux = nullSafe(line.getScannedAuxQty());
        if (scannedAux.compareTo(submittedAux) < 0) {
            scannedAux = submittedAux;
        }
        BigDecimal pendingAux = scannedAux.subtract(submittedAux);
        boolean changed = false;
        if (isInputMapsToAux(line)) {
            if (pending.compareTo(BigDecimal.ZERO) > 0 && pendingAux.compareTo(BigDecimal.ZERO) <= 0) {
                pendingAux = convertByPlanRate(pending, line.getPlanQty(), line.getPlanAuxQty());
                scannedAux = submittedAux.add(pendingAux);
                changed = true;
            } else if (pendingAux.compareTo(BigDecimal.ZERO) > 0 && pending.compareTo(BigDecimal.ZERO) <= 0) {
                pending = convertByPlanRate(pendingAux, line.getPlanAuxQty(), line.getPlanQty());
                scanned = submitted.add(pending);
                changed = true;
            }
        } else if (pending.compareTo(BigDecimal.ZERO) > 0 && pendingAux.compareTo(BigDecimal.ZERO) <= 0) {
            pendingAux = convertByPlanRate(pending, line.getPlanQty(), line.getPlanAuxQty());
            scannedAux = submittedAux.add(pendingAux);
            changed = true;
        }
        if (changed) {
            line.setScannedQty(scanned);
            line.setScannedAuxQty(scannedAux);
            line.setUpdateTime(LocalDateTime.now());
            lineMapper.updateById(line);
        }
        return changed;
    }

    private ReceiveNoticeLineVo toLineVo(PdaReceiveScanLine line) {
        ReceiveNoticeLineVo vo = new ReceiveNoticeLineVo();
        vo.setLineNo(line.getLineNo());
        vo.setMaterialCode(line.getMaterialCode());
        vo.setMaterialName(line.getMaterialName());
        vo.setSpecification(line.getSpecification());
        vo.setBatchNo(line.getBatchNo());
        vo.setUnitCode(line.getUnitCode());
        vo.setAuxUnitCode(line.getAuxUnitCode());
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

        boolean multi = isMultiUnit(line);
        vo.setMultiUnit(multi);
        if (multi) {
            // 主录件数侧，自动换算到 KG 侧
            boolean inputMapsToAux = isInputMapsToAux(line);
            vo.setInputMapsToAux(inputMapsToAux);
            vo.setInputUnitCode(inputMapsToAux ? line.getAuxUnitCode() : line.getUnitCode());
            vo.setAutoUnitCode(inputMapsToAux ? line.getUnitCode() : line.getAuxUnitCode());
            vo.setPlanAuxQty(line.getPlanAuxQty());
            BigDecimal submittedAux = nullSafe(line.getSubmittedAuxQty());
            BigDecimal scannedAux = nullSafe(line.getScannedAuxQty());
            if (scannedAux.compareTo(submittedAux) < 0) {
                scannedAux = submittedAux;
            }
            // 扫码中旧数据：仅有库存待提交、无计价待提交时，按计划比例回填，保证可编辑展示
            BigDecimal pendingAux = scannedAux.subtract(submittedAux);
            if (pending.compareTo(BigDecimal.ZERO) > 0 && pendingAux.compareTo(BigDecimal.ZERO) <= 0) {
                pendingAux = convertByPlanRate(pending, line.getPlanQty(), line.getPlanAuxQty());
                scannedAux = submittedAux.add(pendingAux);
            } else if (pendingAux.compareTo(BigDecimal.ZERO) > 0 && pending.compareTo(BigDecimal.ZERO) <= 0
                    && inputMapsToAux) {
                pending = convertByPlanRate(pendingAux, line.getPlanAuxQty(), line.getPlanQty());
                scanned = submitted.add(pending);
                vo.setScannedQty(scanned);
                vo.setPendingSubmitQty(pending.compareTo(BigDecimal.ZERO) > 0 ? pending : BigDecimal.ZERO);
                vo.setRemainQty(nullSafe(line.getPlanQty()).subtract(submitted));
            }
            vo.setScannedAuxQty(scannedAux);
            vo.setSubmittedAuxQty(submittedAux);
            vo.setPendingSubmitAuxQty(pendingAux.compareTo(BigDecimal.ZERO) > 0 ? pendingAux : BigDecimal.ZERO);
            BigDecimal remainQty = nullSafe(line.getPlanQty()).subtract(submitted);
            if (remainQty.compareTo(BigDecimal.ZERO) < 0) {
                remainQty = BigDecimal.ZERO;
            }
            vo.setRemainQty(remainQty);
            BigDecimal remainAux = nullSafe(line.getPlanAuxQty()).subtract(submittedAux);
            if (remainAux.compareTo(BigDecimal.ZERO) < 0) {
                remainAux = BigDecimal.ZERO;
            }
            // 主录件数时以副单位可处理为准，不因库存可处理为 0 强行清零副单位余量
            if (!inputMapsToAux && remainQty.compareTo(BigDecimal.ZERO) <= 0) {
                remainAux = BigDecimal.ZERO;
            } else if (remainQty.compareTo(BigDecimal.ZERO) > 0
                    && nullSafe(line.getPlanQty()).compareTo(BigDecimal.ZERO) > 0
                    && nullSafe(line.getPlanAuxQty()).compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainAuxByStock = convertByPlanRate(remainQty, line.getPlanQty(), line.getPlanAuxQty());
                if (remainAux.compareTo(remainAuxByStock) > 0) {
                    remainAux = remainAuxByStock;
                }
            }
            vo.setRemainAuxQty(remainAux);
        } else {
            vo.setInputMapsToAux(false);
            vo.setInputUnitCode(line.getUnitCode());
            vo.setAutoUnitCode(null);
        }

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

    /** 入库按分录仓 → 物料默认仓 → 生产订单仓自动分配，跳过 CK004 未分配。 */
    private String resolveLineWarehouse(PdaReceiveScanLine line, KingdeeReceiveBillLineVo kdLine,
                                        ReceiveNoticeSubmitRequest req, Map<String, String> cache) {
        String assigned = erpWarehouseResolver.firstAssigned(
                line != null ? line.getErpStockCode() : null,
                kdLine != null ? kdLine.getStockWarehouseCode() : null,
                req != null ? req.getErpWarehouseCode() : null);
        if (StringUtils.hasText(assigned)) {
            return assigned;
        }
        return resolveAutoWarehouse(kdLine, null, cache);
    }

    private String resolveAutoWarehouse(KingdeeReceiveBillLineVo kdLine, String rawStock,
                                        Map<String, String> cache) {
        String assigned = erpWarehouseResolver.firstAssigned(rawStock,
                kdLine != null ? kdLine.getStockWarehouseCode() : null);
        if (StringUtils.hasText(assigned)) {
            return assigned;
        }
        if (kdLine == null) {
            return null;
        }
        Map<String, String> localCache = cache != null ? cache : new HashMap<>();
        if (StringUtils.hasText(kdLine.getMaterialCode())) {
            String materialKey = "M:" + kdLine.getMaterialCode().trim();
            String fromMaterial = localCache.computeIfAbsent(materialKey,
                    key -> kingdeeCloudService.resolveMaterialDefaultStockNumber(kdLine.getMaterialCode()));
            assigned = erpWarehouseResolver.firstAssigned(fromMaterial);
            if (StringUtils.hasText(assigned)) {
                return assigned;
            }
        }
        if (kdLine.getMoId() != null || StringUtils.hasText(kdLine.getMoBillNo())) {
            String moKey = "MO:" + (kdLine.getMoId() != null ? kdLine.getMoId() : "") + ":"
                    + firstNonBlank(kdLine.getMoBillNo(), "") + ":"
                    + (kdLine.getMoEntryId() != null ? kdLine.getMoEntryId() : "") + ":"
                    + (kdLine.getMoEntrySeq() != null ? kdLine.getMoEntrySeq() : "");
            String fromMo = localCache.computeIfAbsent(moKey,
                    key -> kingdeeCloudService.resolveMoStockNumber(
                            kdLine.getMoId(), kdLine.getMoBillNo(),
                            kdLine.getMoEntryId(), kdLine.getMoEntrySeq()));
            assigned = erpWarehouseResolver.firstAssigned(fromMo);
            if (StringUtils.hasText(assigned)) {
                return assigned;
            }
        }
        return null;
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
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    billType.getLabel() + "不存在、未审核或金蝶查询失败: " + billNo);
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
            return resolveInboundLocationCode(
                    warehouse, line.getMaterialCode(), batchNoMat, autoAllocate, requestedLocation);
        }
        Inventory stock = resolveOutboundStock(warehouse, line, null, batchNoMat, requestedLocation);
        return stock.getLocationCode() != null ? stock.getLocationCode() : "";
    }

    /**
     * 入库库位：自动分配 / 手动指定 / 不选（空串，仅仓库维度）。
     */
    private String resolveInboundLocationCode(String warehouse, String materialCode, String batchNo,
                                              boolean autoAllocate, String requestedLocation) {
        if (!StringUtils.hasText(warehouse)) {
            return "";
        }
        try {
            if (autoAllocate) {
                String code = locationAllocationService.resolveInboundLocation(
                        warehouse.trim(), materialCode, batchNo, null);
                return code != null ? code : "";
            }
            if (StringUtils.hasText(requestedLocation)) {
                return locationAllocationService.resolveInboundLocation(
                        warehouse.trim(), materialCode, batchNo, requestedLocation.trim());
            }
        } catch (BusinessException ex) {
            // 手动指定无效库位时明确失败；自动分配失败则降级为仅仓库
            if (!autoAllocate && StringUtils.hasText(requestedLocation)) {
                throw ex;
            }
            log.warn("入库库位分配失败，降级为仅仓库 warehouse={} material={} msg={}",
                    warehouse, materialCode, ex.getMessage());
        }
        return "";
    }

    /**
     * 解析出库扣减用的库存行：金蝶仓→WMS 仓映射，批号放宽，全仓兜底；库位允许为空。
     */
    private Inventory resolveOutboundStock(String sessionWarehouse, PdaReceiveScanLine line,
                                           KingdeeReceiveBillLineVo kdLine, String batchNoMat,
                                           String requestedLocation) {
        if (StringUtils.hasText(requestedLocation) && StringUtils.hasText(sessionWarehouse)) {
            Inventory exact = inventoryMapper.selectByKey(
                    sessionWarehouse.trim(), requestedLocation.trim(), line.getMaterialCode(),
                    batchNoMat != null ? batchNoMat : "");
            if (exact != null && exact.getAvailableQty() != null
                    && exact.getAvailableQty().compareTo(BigDecimal.ZERO) > 0) {
                return exact;
            }
        }
        String preferredWms = resolveOutboundWmsWarehouse(sessionWarehouse, line, kdLine);
        Inventory stock = findOutboundStock(preferredWms, line.getMaterialCode(), batchNoMat);
        if (stock == null && StringUtils.hasText(preferredWms)) {
            stock = findOutboundStock(preferredWms, line.getMaterialCode(), null);
        }
        if (stock == null) {
            stock = findOutboundStock(null, line.getMaterialCode(), batchNoMat);
        }
        if (stock == null) {
            stock = findOutboundStock(null, line.getMaterialCode(), null);
        }
        if (stock != null) {
            return stock;
        }
        throw new BusinessException(ErrorCode.CONFLICT,
                "未找到可出库库存: " + line.getMaterialCode()
                        + (StringUtils.hasText(preferredWms) ? "（仓库 " + preferredWms + "）" : ""),
                "NO_STOCK");
    }

    private Inventory findOutboundStock(String warehouse, String materialCode, String batchNo) {
        if (!StringUtils.hasText(materialCode)) {
            return null;
        }
        LambdaQueryWrapper<Inventory> q = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getMaterialCode, materialCode)
                .gt(Inventory::getAvailableQty, BigDecimal.ZERO)
                .orderByDesc(Inventory::getAvailableQty)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY");
        if (StringUtils.hasText(warehouse)) {
            q.eq(Inventory::getWarehouseCode, warehouse.trim());
        }
        if (batchNo != null) {
            q.eq(Inventory::getBatchNo, batchNo);
        }
        return inventoryMapper.selectOne(q);
    }

    /**
     * 出库扣库存使用 WMS 仓库编码：金蝶 FStockId / 会话仓 → 反查 WMS；必要时按物料有货仓库兜底。
     */
    private String resolveOutboundWmsWarehouse(String sessionWarehouse, PdaReceiveScanLine line,
                                               KingdeeReceiveBillLineVo kdLine) {
        String erpOrSession = firstNonBlank(
                line != null ? line.getErpStockCode() : null,
                kdLine != null ? kdLine.getStockWarehouseCode() : null,
                sessionWarehouse);
        String wms = erpWarehouseResolver.resolveWmsCode(erpOrSession);
        if (StringUtils.hasText(wms)) {
            Inventory hit = findOutboundStock(wms, line.getMaterialCode(), null);
            if (hit != null) {
                return wms;
            }
        }
        Inventory any = findOutboundStock(null, line.getMaterialCode(), null);
        if (any != null && StringUtils.hasText(any.getWarehouseCode())) {
            return any.getWarehouseCode();
        }
        return firstNonBlank(wms, sessionWarehouse, "WH01");
    }

    private void validateInboundQtyAgainstKingdee(List<PdaReceiveScanLine> lines, KingdeeReceiveBillVo kdBill) {
        for (PdaReceiveScanLine line : lines) {
            BigDecimal qty = nullSafe(line.getScannedQty()).subtract(nullSafe(line.getSubmittedQty()));
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            KingdeeReceiveBillLineVo kdLine = findKdLine(kdBill, line);
            BigDecimal remain = resolveInboundRemainQty(line.getBillType(), kdLine);
            if (remain != null && qty.compareTo(remain) > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        String.format("第%d行「%s」本次入库 %s 超过%s %s",
                                line.getLineNo(),
                                firstNonBlank(line.getMaterialName(), line.getMaterialCode()),
                                qty.stripTrailingZeros().toPlainString(),
                                inboundRemainLabel(line.getBillType()),
                                remain.stripTrailingZeros().toPlainString()),
                        "ERP_IN_STOCK_QTY_EXCEEDED");
            }
        }
    }

    /**
     * 入库可处理上限：收料/生产汇报按金蝶「剩余可入库」；
     * 退货通知等无该字段的单据按通知数量，避免套用收料口径把可退量算成 0。
     */
    private BigDecimal resolveInboundRemainQty(String billTypeCode, KingdeeReceiveBillLineVo kdLine) {
        if (kdLine == null) {
            return null;
        }
        if (!usesKingdeeInStockJoin(billTypeCode)) {
            BigDecimal plan = resolvePlanQty(kdLine);
            return plan.compareTo(BigDecimal.ZERO) > 0 ? plan : null;
        }
        return KingdeeReceiveRemainQty.resolve(
                kdLine.getRemainInStockBaseQty(),
                kdLine.getQualifiedQty(),
                kdLine.getPlanQty(),
                kdLine.getInStockJoinBaseQty());
    }

    private String inboundRemainLabel(String billTypeCode) {
        if (NoticeBillType.PRODUCTION_IN.getCode().equalsIgnoreCase(billTypeCode)) {
            return "生产汇报单剩余可入库";
        }
        if (NoticeBillType.PURCHASE_RECEIVE.getCode().equalsIgnoreCase(billTypeCode)) {
            return "收料单剩余可入库";
        }
        return "单据可处理数量";
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
                .filter(this::hasProcessableRemain)
                .toList();
        if (open.isEmpty()) {
            // 有物料行但已无可处理：返回首行，由上层给出「已收满」提示
            return candidates.get(0);
        }
        for (PdaReceiveScanLine line : open) {
            BigDecimal remain = nullSafe(line.getPlanQty()).subtract(nullSafe(line.getSubmittedQty()));
            if (remain.compareTo(qty) == 0) {
                return line;
            }
        }
        return open.stream()
                .filter(l -> {
                    BigDecimal remain = nullSafe(l.getPlanQty()).subtract(nullSafe(l.getSubmittedQty()));
                    return remain.compareTo(BigDecimal.ZERO) <= 0 || remain.compareTo(qty) >= 0;
                })
                .min((a, b) -> {
                    BigDecimal ra = nullSafe(a.getPlanQty()).subtract(nullSafe(a.getSubmittedQty()));
                    BigDecimal rb = nullSafe(b.getPlanQty()).subtract(nullSafe(b.getSubmittedQty()));
                    if (ra.compareTo(BigDecimal.ZERO) <= 0 && rb.compareTo(BigDecimal.ZERO) <= 0) {
                        return 0;
                    }
                    if (ra.compareTo(BigDecimal.ZERO) <= 0) {
                        return 1;
                    }
                    if (rb.compareTo(BigDecimal.ZERO) <= 0) {
                        return -1;
                    }
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
