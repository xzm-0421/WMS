package com.wms.pdareceive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.barcode.util.BatchNoNormalizer;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.integration.kingdee.KingdeeErpWarehouseResolver;
import com.wms.integration.kingdee.KingdeeInStockEntryLink;
import com.wms.integration.kingdee.KingdeePickMtrlActualQtyBuilder;
import com.wms.integration.kingdee.KingdeePickMtrlRequest;
import com.wms.integration.kingdee.KingdeePrdInStockRequest;
import com.wms.integration.kingdee.KingdeeReturnMtrlActualQtyBuilder;
import com.wms.integration.kingdee.KingdeePurchaseInStockRequest;
import com.wms.integration.kingdee.KingdeeReceiveBillSourceResolver;
import com.wms.integration.kingdee.KingdeeResponseMessageFormatter;
import com.wms.integration.kingdee.KingdeeReturnMtrlRequest;
import com.wms.integration.kingdee.KingdeeSalReturnStockRequest;
import com.wms.integration.kingdee.KingdeeSalOutStockEntryFill;
import com.wms.integration.kingdee.KingdeeSubPickMtrlRequest;
import com.wms.integration.kingdee.KingdeeSubReturnMtrlRequest;
import com.wms.integration.kingdee.KingdeeSyncResult;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillLineVo;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;
import com.wms.integration.service.ErpSyncLogService;
import com.wms.noticebill.NoticeBillProviderRegistry;
import com.wms.noticebill.NoticeBillType;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.mapper.InventoryTransactionMapper;
import com.wms.pdainbound.entity.PdaInboundRecord;
import com.wms.pdainbound.mapper.PdaInboundRecordMapper;
import com.wms.pdareceive.entity.PdaReceiveScanLine;
import com.wms.pdareceive.entity.PdaReceiveSubmitBatch;
import com.wms.pdareceive.mapper.PdaReceiveScanLineMapper;
import com.wms.pdareceive.mapper.PdaReceiveSubmitBatchMapper;
import com.wms.system.entity.SysUserKingdeeMap;
import com.wms.system.service.SysUserKingdeeMapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdaReceiveSubmitBatchService {

    private final PdaReceiveSubmitBatchMapper batchMapper;
    private final PdaInboundRecordMapper recordMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final PdaReceiveScanLineMapper scanLineMapper;
    private final NoticeBillProviderRegistry noticeBillProviderRegistry;
    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeErpWarehouseResolver erpWarehouseResolver;
    private final KingdeeReceiveBillSourceResolver receiveBillSourceResolver;
    private final KingdeeCloudProperties kingdeeCloudProperties;
    private final ErpSyncLogService erpSyncLogService;
    private final SysUserKingdeeMapService userKingdeeMapService;

    public PageResult<PdaReceiveSubmitBatch> page(String billNo, String erpSyncStatus, long current, long size) {
        return page(billNo, erpSyncStatus, null, null, null, current, size);
    }

    public PageResult<PdaReceiveSubmitBatch> page(String billNo, String erpSyncStatus, String direction,
                                                  String billType, String batchNo, long current, long size) {
        LambdaQueryWrapper<PdaReceiveSubmitBatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(billNo), PdaReceiveSubmitBatch::getBillNo, billNo)
                .like(StringUtils.hasText(batchNo), PdaReceiveSubmitBatch::getBatchNo, batchNo)
                .eq(StringUtils.hasText(erpSyncStatus), PdaReceiveSubmitBatch::getErpSyncStatus, erpSyncStatus)
                .eq(StringUtils.hasText(direction), PdaReceiveSubmitBatch::getDirection, direction)
                .eq(StringUtils.hasText(billType), PdaReceiveSubmitBatch::getBillType, billType)
                .orderByDesc(PdaReceiveSubmitBatch::getSubmitTime);
        var page = batchMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> getOutboundBatchDetail(String batchNo) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "出库批次不存在");
        }
        if (!"OUTBOUND".equalsIgnoreCase(batch.getDirection())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该批次不是出库记录");
        }
        LocalDateTime from = batch.getSubmitTime() != null
                ? batch.getSubmitTime().minusMinutes(2)
                : LocalDateTime.now().minusDays(1);
        LocalDateTime to = batch.getSubmitTime() != null
                ? batch.getSubmitTime().plusMinutes(10)
                : LocalDateTime.now();
        List<InventoryTransaction> lines = inventoryTransactionMapper.selectList(new LambdaQueryWrapper<InventoryTransaction>()
                .eq(InventoryTransaction::getSourceOrderNo, batch.getBillNo())
                .ge(InventoryTransaction::getOperationTime, from)
                .le(InventoryTransaction::getOperationTime, to)
                .orderByAsc(InventoryTransaction::getId));
        Map<String, Object> result = new HashMap<>();
        result.put("batch", batch);
        result.put("lines", lines);
        return result;
    }

    /**
     * 收料入库批次详情：批次头 + PDA 入库明细 + 金蝶同步日志（含报错）。
     */
    public Map<String, Object> getInboundBatchDetail(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次号不能为空");
        }
        String no = batchNo.trim();
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, no));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入库批次不存在");
        }
        if (StringUtils.hasText(batch.getDirection()) && !"INBOUND".equalsIgnoreCase(batch.getDirection())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该批次不是入库记录");
        }
        List<PdaInboundRecord> records = recordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSubmitBatchNo, no)
                .eq(PdaInboundRecord::getDeleted, 0)
                .orderByAsc(PdaInboundRecord::getId));
        List<?> syncLogs = erpSyncLogService.listBySource(ErpSyncLogService.SOURCE_PDA_INBOUND, no);
        Map<String, Object> result = new HashMap<>();
        result.put("batch", batch);
        result.put("records", records);
        result.put("syncLogs", syncLogs);
        return result;
    }

    /** 每个收料单取最近一条提交批次（用于列表展示金蝶入库单号） */
    public Map<String, PdaReceiveSubmitBatch> latestBatchByBillNos(List<String> billNos) {
        if (billNos == null || billNos.isEmpty()) {
            return Map.of();
        }
        List<PdaReceiveSubmitBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .in(PdaReceiveSubmitBatch::getBillNo, billNos)
                .orderByDesc(PdaReceiveSubmitBatch::getSubmitTime));
        Map<String, PdaReceiveSubmitBatch> latest = new LinkedHashMap<>();
        for (PdaReceiveSubmitBatch batch : batches) {
            if (StringUtils.hasText(batch.getBillNo())) {
                latest.putIfAbsent(batch.getBillNo(), batch);
            }
        }
        return latest;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncBatchToErp(String batchNo) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if ("PRODUCTION_IN".equalsIgnoreCase(batch.getBillType())) {
            return syncPrdInStockBatch(batchNo);
        }
        if ("PRODUCTION_RET_STOCK".equalsIgnoreCase(batch.getBillType())) {
            return syncPrdRetStockBatch(batchNo);
        }
        if ("PRODUCTION_RETURN".equalsIgnoreCase(batch.getBillType())) {
            return syncReturnMtrlBatch(batchNo, null);
        }
        if ("OUTSOURCE_RETURN".equalsIgnoreCase(batch.getBillType())) {
            return syncSubReturnMtrlBatch(batchNo, null);
        }
        if ("SALES_RETURN".equalsIgnoreCase(batch.getBillType())) {
            return syncSalReturnStockBatch(batchNo);
        }
        if ("SUCCESS".equals(batch.getErpSyncStatus())) {
            Map<String, Object> cached = new HashMap<>();
            cached.put("batchNo", batchNo);
            cached.put("successCount", batch.getLineCount());
            cached.put("failedCount", 0);
            cached.put("erpSyncStatus", batch.getErpSyncStatus());
            cached.put("erpBillNo", batch.getErpBillNo());
            cached.put("message", "批次已同步成功");
            return cached;
        }

        List<PdaInboundRecord> records = recordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSubmitBatchNo, batchNo)
                .eq(PdaInboundRecord::getDeleted, 0));
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次下无入库记录");
        }

        List<PdaInboundRecord> pending = records.stream()
                .filter(r -> !"SUCCESS".equals(r.getErpSyncStatus()))
                .collect(Collectors.toList());
        if (pending.isEmpty()) {
            batch.setErpSyncStatus("SUCCESS");
            batchMapper.updateById(batch);
            return buildSyncResponse(batchNo, records.size(), 0, batch);
        }

        for (PdaInboundRecord record : pending) {
            record.setErpSyncStatus("SYNCING");
            record.setUpdateTime(LocalDateTime.now());
            recordMapper.updateById(record);
        }

        KingdeeSyncResult result;
        try {
            KingdeePurchaseInStockRequest kdReq = buildPurchaseRequest(batch, pending);
            result = kingdeeCloudService.syncPurchaseInStock(kdReq);
        } catch (BusinessException ex) {
            result = KingdeeSyncResult.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .build();
        }
        int retry = maxRetryCount(pending) + 1;

        erpSyncLogService.writeLog(
                ErpSyncLogService.SOURCE_PDA_INBOUND,
                batchNo,
                combineWorkflowRequestPayload(result),
                combineWorkflowResponsePayload(result),
                result.isSuccess(),
                result.isSuccess() ? null : result.getMessage(),
                retry);

        LocalDateTime syncTime = LocalDateTime.now();
        for (PdaInboundRecord record : pending) {
            record.setErpSyncTime(syncTime);
            record.setErpRetryCount(retry);
            if (result.isSuccess()) {
                record.setErpSyncStatus("SUCCESS");
                record.setErpBillNo(result.getBillNo());
                record.setErpSyncMessage(ErpSyncLogService.truncateMessage(result.getMessage()));
            } else {
                record.setErpSyncStatus("FAILED");
                record.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                        KingdeeResponseMessageFormatter.format(result.getMessage())));
            }
            record.setUpdateTime(syncTime);
            recordMapper.updateById(record);
        }

        if (!result.isSuccess()) {
            rollbackPurchaseReceiveSubmittedQty(batch, pending);
        } else {
            // 失败回滚后再重试成功时，按成功记录补齐已处理
            reconcilePurchaseReceiveSubmittedQty(batch, pending);
        }

        long successRecords = records.stream().filter(r -> "SUCCESS".equals(r.getErpSyncStatus())).count();
        int failed = (int) (records.size() - successRecords);
        if (failed == 0) {
            batch.setErpSyncStatus("SUCCESS");
            batch.setErpBillNo(result.getBillNo());
            batch.setErpSyncMessage(result.isAudited()
                    ? "保存、提交并审核成功: " + result.getBillNo()
                    : "同步成功: " + result.getBillNo());
        } else if (successRecords > 0) {
            batch.setErpSyncStatus("PARTIAL");
            batch.setErpBillNo(result.getBillNo());
            batch.setErpSyncMessage("部分成功");
        } else {
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(result.getMessage())));
        }
        batchMapper.updateById(batch);

        return buildSyncResponse(batchNo, (int) successRecords, failed, batch);
    }

    /**
     * 采购入库同步失败时回滚扫码行「已处理」，避免未下推成功的数量占用已处理/推高计划。
     * 扫描量保留，便于用户改完后再次提交。
     */
    private void rollbackPurchaseReceiveSubmittedQty(PdaReceiveSubmitBatch batch,
                                                     List<PdaInboundRecord> failedRecords) {
        if (batch == null || failedRecords == null || failedRecords.isEmpty()) {
            return;
        }
        String billType = batch.getBillType();
        boolean purchaseReceive = !StringUtils.hasText(billType)
                || NoticeBillType.PURCHASE_RECEIVE.getCode().equalsIgnoreCase(billType);
        if (!purchaseReceive) {
            return;
        }
        String billNo = batch.getBillNo();
        if (!StringUtils.hasText(billNo)) {
            return;
        }
        for (PdaInboundRecord record : failedRecords) {
            if (record == null || record.getSourceLineNo() == null) {
                continue;
            }
            PdaReceiveScanLine line = scanLineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                    .eq(PdaReceiveScanLine::getBillNo, billNo)
                    .eq(PdaReceiveScanLine::getBillType, NoticeBillType.PURCHASE_RECEIVE.getCode())
                    .eq(PdaReceiveScanLine::getLineNo, record.getSourceLineNo()));
            if (line == null) {
                line = scanLineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                        .eq(PdaReceiveScanLine::getBillNo, billNo)
                        .eq(PdaReceiveScanLine::getLineNo, record.getSourceLineNo()));
            }
            if (line == null) {
                continue;
            }
            BigDecimal qty = record.getQuantity() == null ? BigDecimal.ZERO : record.getQuantity();
            BigDecimal next = (line.getSubmittedQty() == null ? BigDecimal.ZERO : line.getSubmittedQty()).subtract(qty);
            if (next.compareTo(BigDecimal.ZERO) < 0) {
                next = BigDecimal.ZERO;
            }
            line.setSubmittedQty(next);
            BigDecimal auxQty = record.getAuxQuantity() == null ? BigDecimal.ZERO : record.getAuxQuantity();
            if (auxQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal nextAux = (line.getSubmittedAuxQty() == null ? BigDecimal.ZERO : line.getSubmittedAuxQty())
                        .subtract(auxQty);
                if (nextAux.compareTo(BigDecimal.ZERO) < 0) {
                    nextAux = BigDecimal.ZERO;
                }
                line.setSubmittedAuxQty(nextAux);
            }
            line.setUpdateTime(LocalDateTime.now());
            scanLineMapper.updateById(line);
            log.info("Rollback submittedQty after ERP sync fail billNo={} line={} qty={} nextSubmitted={}",
                    billNo, line.getLineNo(), qty.stripTrailingZeros().toPlainString(),
                    next.stripTrailingZeros().toPlainString());
        }
    }

    /**
     * 同步成功后：已处理至少覆盖本批成功数量（兼容先失败回滚再重试成功）。
     */
    private void reconcilePurchaseReceiveSubmittedQty(PdaReceiveSubmitBatch batch,
                                                      List<PdaInboundRecord> successRecords) {
        if (batch == null || successRecords == null || successRecords.isEmpty()) {
            return;
        }
        String billType = batch.getBillType();
        boolean purchaseReceive = !StringUtils.hasText(billType)
                || NoticeBillType.PURCHASE_RECEIVE.getCode().equalsIgnoreCase(billType);
        if (!purchaseReceive || !StringUtils.hasText(batch.getBillNo())) {
            return;
        }
        Map<Integer, BigDecimal> qtyByLine = new HashMap<>();
        for (PdaInboundRecord record : successRecords) {
            if (record == null || record.getSourceLineNo() == null) {
                continue;
            }
            BigDecimal qty = record.getQuantity() == null ? BigDecimal.ZERO : record.getQuantity();
            qtyByLine.merge(record.getSourceLineNo(), qty, BigDecimal::add);
        }
        for (Integer lineNo : qtyByLine.keySet()) {
            PdaReceiveScanLine line = scanLineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                    .eq(PdaReceiveScanLine::getBillNo, batch.getBillNo())
                    .eq(PdaReceiveScanLine::getBillType, NoticeBillType.PURCHASE_RECEIVE.getCode())
                    .eq(PdaReceiveScanLine::getLineNo, lineNo));
            if (line == null) {
                line = scanLineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                        .eq(PdaReceiveScanLine::getBillNo, batch.getBillNo())
                        .eq(PdaReceiveScanLine::getLineNo, lineNo));
            }
            if (line == null) {
                continue;
            }
            // 汇总该行全部 SUCCESS 记录，避免只加本批导致少计
            List<PdaInboundRecord> allSuccess = recordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                    .eq(PdaInboundRecord::getSourceBillNo, batch.getBillNo())
                    .eq(PdaInboundRecord::getSourceLineNo, lineNo)
                    .eq(PdaInboundRecord::getErpSyncStatus, "SUCCESS")
                    .eq(PdaInboundRecord::getDeleted, 0));
            BigDecimal successSum = BigDecimal.ZERO;
            BigDecimal successAuxSum = BigDecimal.ZERO;
            for (PdaInboundRecord r : allSuccess) {
                if (r.getQuantity() != null) {
                    successSum = successSum.add(r.getQuantity());
                }
                if (r.getAuxQuantity() != null) {
                    successAuxSum = successAuxSum.add(r.getAuxQuantity());
                }
            }
            BigDecimal cur = line.getSubmittedQty() == null ? BigDecimal.ZERO : line.getSubmittedQty();
            if (successSum.compareTo(cur) > 0) {
                line.setSubmittedQty(successSum);
            }
            BigDecimal curAux = line.getSubmittedAuxQty() == null ? BigDecimal.ZERO : line.getSubmittedAuxQty();
            if (successAuxSum.compareTo(curAux) > 0) {
                line.setSubmittedAuxQty(successAuxSum);
            }
            if (nullSafeScanned(line).compareTo(nullSafeSubmitted(line)) < 0) {
                line.setScannedQty(line.getSubmittedQty());
            }
            line.setUpdateTime(LocalDateTime.now());
            scanLineMapper.updateById(line);
        }
    }

    private static BigDecimal nullSafeScanned(PdaReceiveScanLine line) {
        return line.getScannedQty() == null ? BigDecimal.ZERO : line.getScannedQty();
    }

    private static BigDecimal nullSafeSubmitted(PdaReceiveScanLine line) {
        return line.getSubmittedQty() == null ? BigDecimal.ZERO : line.getSubmittedQty();
    }

    /**
     * PDA 生产领料：回写金蝶实发数量后 Submit + Audit（部分领料同样审核）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncPickMtrlBatch(String batchNo, List<KingdeePickMtrlRequest.Line> lines) {
        return syncExistingPickActualQtyBatch(
                batchNo,
                NoticeBillType.PRODUCTION_ISSUE,
                kingdeeCloudProperties.getPickMtrlFormId(),
                false,
                "生产领料");
    }

    /**
     * PDA 生产补料：回写金蝶实发数量后 Submit + 工作流审批（部分补料同样审核）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncFeedMtrlBatch(String batchNo) {
        return syncExistingPickActualQtyBatch(
                batchNo,
                NoticeBillType.PRODUCTION_FEED,
                kingdeeCloudProperties.getFeedMtrlFormId(),
                true,
                "生产补料");
    }

    /**
     * PDA 委外补料：回写金蝶实发数量后 Submit + 工作流审批（部分补料同样审核）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncSubFeedMtrlBatch(String batchNo) {
        return syncExistingPickActualQtyBatch(
                batchNo,
                NoticeBillType.OUTSOURCE_FEED,
                kingdeeCloudProperties.getSubFeedMtrlFormId(),
                true,
                "委外补料");
    }

    /**
     * PDA 委外领料：回写金蝶实发数量后 Submit + Audit（部分领料同样审核）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncSubPickMtrlBatch(String batchNo, List<KingdeeSubPickMtrlRequest.Line> lines) {
        return syncExistingPickActualQtyBatch(
                batchNo,
                NoticeBillType.OUTSOURCE_ISSUE,
                kingdeeCloudProperties.getSubPickMtrlFormId(),
                false,
                "委外领料");
    }

    /**
     * PDA 生产汇报入库：按扫码量 Save 新建生产入库单（关联汇报反写选单量）→ Submit + Audit。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncPrdInStockBatch(String batchNo) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        if (!StringUtils.hasText(batch.getBillNo())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次缺少生产汇报单号，无法生成入库单");
        }

        List<PdaInboundRecord> records = recordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSubmitBatchNo, batchNo)
                .eq(PdaInboundRecord::getDeleted, 0));
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次下无入库记录，无法同步生产入库单");
        }
        List<PdaInboundRecord> pending = records.stream()
                .filter(r -> !"SUCCESS".equals(r.getErpSyncStatus()))
                .collect(Collectors.toList());
        if (pending.isEmpty()) {
            batch.setErpSyncStatus("SUCCESS");
            batchMapper.updateById(batch);
            return buildSyncResponse(batchNo, records.size(), 0, batch);
        }

        for (PdaInboundRecord record : pending) {
            record.setErpSyncStatus("SYNCING");
            record.setUpdateTime(LocalDateTime.now());
            recordMapper.updateById(record);
        }

        KingdeeSyncResult result;
        try {
            KingdeePrdInStockRequest kdReq = buildPrdInStockRequest(batch, pending);
            result = kingdeeCloudService.syncPrdInStock(kdReq);
        } catch (BusinessException ex) {
            result = KingdeeSyncResult.builder().success(false).message(ex.getMessage()).build();
        } catch (IllegalStateException ex) {
            result = KingdeeSyncResult.builder().success(false).message(ex.getMessage()).build();
        }

        int retry = maxRetryCount(pending) + 1;
        erpSyncLogService.writeLog(
                ErpSyncLogService.SOURCE_PDA_INBOUND,
                batchNo,
                combineWorkflowRequestPayload(result),
                combineWorkflowResponsePayload(result),
                result.isSuccess(),
                result.isSuccess() ? null : result.getMessage(),
                retry);

        LocalDateTime now = LocalDateTime.now();
        if (result.isSuccess()) {
            for (PdaInboundRecord record : pending) {
                record.setErpSyncStatus("SUCCESS");
                record.setErpBillNo(result.getBillNo());
                record.setErpSyncMessage(result.isAudited()
                        ? "生产入库已保存并审核: " + result.getBillNo()
                        : "生产入库已保存: " + result.getBillNo());
                record.setErpSyncTime(now);
                record.setErpRetryCount(retry);
                record.setUpdateTime(now);
                recordMapper.updateById(record);
            }
            batch.setErpSyncStatus("SUCCESS");
            batch.setErpBillNo(result.getBillNo());
            batch.setErpSyncMessage(result.isAudited()
                    ? "生产入库保存、提交并审核成功: " + result.getBillNo()
                    : "生产入库同步成功: " + result.getBillNo());
            reconcileCreateInboundSubmittedQty(batch, pending);
        } else {
            for (PdaInboundRecord record : pending) {
                record.setErpSyncStatus("FAILED");
                record.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                        KingdeeResponseMessageFormatter.format(result.getMessage())));
                record.setErpSyncTime(now);
                record.setErpRetryCount(retry);
                record.setUpdateTime(now);
                recordMapper.updateById(record);
            }
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(result.getMessage())));
            rollbackCreateInboundSubmittedQty(batch, pending);
        }
        batchMapper.updateById(batch);
        long successRecords = records.stream().filter(r -> "SUCCESS".equals(r.getErpSyncStatus())).count();
        int failed = (int) (records.size() - successRecords);
        return buildSyncResponse(batchNo, (int) successRecords, failed, batch);
    }

    private KingdeePrdInStockRequest buildPrdInStockRequest(PdaReceiveSubmitBatch batch,
                                                            List<PdaInboundRecord> records) {
        String morptBillNo = batch.getBillNo().trim();
        KingdeeReceiveBillVo morpt = noticeBillProviderRegistry.require(NoticeBillType.PRODUCTION_IN)
                .getBill(morptBillNo);
        if (morpt == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "生产汇报单不存在或已无可入量: " + morptBillNo);
        }
        Map<Integer, KingdeeReceiveBillLineVo> byLineNo = new LinkedHashMap<>();
        Map<Long, KingdeeReceiveBillLineVo> byEntryId = new LinkedHashMap<>();
        if (morpt.getLines() != null) {
            for (KingdeeReceiveBillLineVo line : morpt.getLines()) {
                if (line == null) {
                    continue;
                }
                if (line.getLineNo() != null) {
                    byLineNo.put(line.getLineNo(), line);
                }
                if (line.getEntryId() != null && line.getEntryId() > 0) {
                    byEntryId.put(line.getEntryId(), line);
                }
            }
        }

        List<KingdeePrdInStockRequest.Line> lines = new ArrayList<>();
        for (PdaInboundRecord record : records) {
            if (record == null || record.getQuantity() == null
                    || record.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            KingdeeReceiveBillLineVo kdLine = null;
            if (record.getSourceEntryId() != null && record.getSourceEntryId() > 0) {
                kdLine = byEntryId.get(record.getSourceEntryId());
            }
            if (kdLine == null && record.getSourceLineNo() != null) {
                kdLine = byLineNo.get(record.getSourceLineNo());
            }
            if (kdLine == null || kdLine.getEntryId() == null || kdLine.getEntryId() <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "生产汇报分录内码缺失，无法下推入库（行 " + record.getSourceLineNo() + "）",
                        "MISSING_MORPT_ENTRY");
            }
            Long morptBillId = firstNonNull(record.getSourceBillId(), morpt.getBillId());
            if (morptBillId == null || morptBillId <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "生产汇报单内码(FID)缺失，无法建立关联反写", "MISSING_MORPT_BILL_ID");
            }
            String warehouse = firstNonBlank(record.getErpStockCode(), record.getWarehouseCode(),
                    kdLine.getStockWarehouseCode());
            if (!StringUtils.hasText(warehouse)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "第" + record.getSourceLineNo() + "行缺少仓库编码", "MISSING_WAREHOUSE");
            }
            lines.add(KingdeePrdInStockRequest.Line.builder()
                    .materialCode(record.getMaterialCode())
                    .materialName(record.getMaterialName())
                    .unitCode(record.getUnitCode())
                    .mustQty(record.getQuantity())
                    .realQty(record.getQuantity())
                    .workShopCode(firstNonBlank(batch.getSupplierCode(), morpt.getSupplierCode()))
                    .warehouseCode(warehouse)
                    .locationCode(record.getLocationCode())
                    .batchNo(record.getBatchNo())
                    .moBillNo(kdLine.getMoBillNo())
                    .moId(kdLine.getMoId())
                    .moEntryId(kdLine.getMoEntryId())
                    .moEntrySeq(kdLine.getMoEntrySeq())
                    .srcEntryId(kdLine.getEntryId())
                    .srcInterId(morptBillId)
                    .srcBillNo(morptBillNo)
                    .srcEntrySeq(kdLine.getLineNo())
                    .inStockType(kingdeeCloudProperties.getPrdInStockInStockType())
                    .sourceLineNo(record.getSourceLineNo())
                    .entryNote(record.getBarcodeContent())
                    .build());
        }
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有可同步的生产入库数量");
        }
        return KingdeePrdInStockRequest.builder()
                .batchNo(batch.getBatchNo())
                .billDate(LocalDate.now())
                .workShopCode(firstNonBlank(batch.getSupplierCode(), morpt.getSupplierCode()))
                .note("WMS PDA 汇报入库 " + morptBillNo)
                .lines(lines)
                .build();
    }

    private void rollbackCreateInboundSubmittedQty(PdaReceiveSubmitBatch batch,
                                                   List<PdaInboundRecord> failedRecords) {
        if (batch == null || failedRecords == null || failedRecords.isEmpty()) {
            return;
        }
        String billType = batch.getBillType();
        if (!NoticeBillType.PRODUCTION_IN.getCode().equalsIgnoreCase(billType)
                && !NoticeBillType.PURCHASE_RECEIVE.getCode().equalsIgnoreCase(billType)
                && StringUtils.hasText(billType)) {
            return;
        }
        String billNo = batch.getBillNo();
        if (!StringUtils.hasText(billNo)) {
            return;
        }
        String typeCode = StringUtils.hasText(billType) ? billType : NoticeBillType.PRODUCTION_IN.getCode();
        for (PdaInboundRecord record : failedRecords) {
            if (record == null || record.getSourceLineNo() == null) {
                continue;
            }
            PdaReceiveScanLine line = scanLineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                    .eq(PdaReceiveScanLine::getBillNo, billNo)
                    .eq(PdaReceiveScanLine::getBillType, typeCode)
                    .eq(PdaReceiveScanLine::getLineNo, record.getSourceLineNo()));
            if (line == null) {
                continue;
            }
            BigDecimal qty = record.getQuantity() == null ? BigDecimal.ZERO : record.getQuantity();
            BigDecimal next = (line.getSubmittedQty() == null ? BigDecimal.ZERO : line.getSubmittedQty()).subtract(qty);
            if (next.compareTo(BigDecimal.ZERO) < 0) {
                next = BigDecimal.ZERO;
            }
            line.setSubmittedQty(next);
            line.setUpdateTime(LocalDateTime.now());
            scanLineMapper.updateById(line);
            log.info("Rollback submittedQty after production-in ERP fail billNo={} line={} qty={} next={}",
                    billNo, line.getLineNo(), qty.stripTrailingZeros().toPlainString(),
                    next.stripTrailingZeros().toPlainString());
        }
    }

    private void reconcileCreateInboundSubmittedQty(PdaReceiveSubmitBatch batch,
                                                    List<PdaInboundRecord> successRecords) {
        if (batch == null || successRecords == null || successRecords.isEmpty()) {
            return;
        }
        if (!NoticeBillType.PRODUCTION_IN.getCode().equalsIgnoreCase(batch.getBillType())) {
            return;
        }
        Map<Integer, BigDecimal> qtyByLine = new HashMap<>();
        for (PdaInboundRecord record : successRecords) {
            if (record == null || record.getSourceLineNo() == null) {
                continue;
            }
            BigDecimal qty = record.getQuantity() == null ? BigDecimal.ZERO : record.getQuantity();
            qtyByLine.merge(record.getSourceLineNo(), qty, BigDecimal::add);
        }
        for (Map.Entry<Integer, BigDecimal> e : qtyByLine.entrySet()) {
            PdaReceiveScanLine line = scanLineMapper.selectOne(new LambdaQueryWrapper<PdaReceiveScanLine>()
                    .eq(PdaReceiveScanLine::getBillNo, batch.getBillNo())
                    .eq(PdaReceiveScanLine::getBillType, NoticeBillType.PRODUCTION_IN.getCode())
                    .eq(PdaReceiveScanLine::getLineNo, e.getKey()));
            if (line == null) {
                continue;
            }
            BigDecimal submitted = line.getSubmittedQty() == null ? BigDecimal.ZERO : line.getSubmittedQty();
            if (submitted.compareTo(e.getValue()) < 0) {
                line.setSubmittedQty(e.getValue());
                line.setUpdateTime(LocalDateTime.now());
                scanLineMapper.updateById(line);
            }
        }
    }

    private static Long firstNonNull(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long v : values) {
            if (v != null && v > 0) {
                return v;
            }
        }
        return null;
    }

    /**
     * PDA 生产退库批次：对已存在的未审核生产退库单执行 Submit + Audit（不再 Save 新建）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncPrdRetStockBatch(String batchNo) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        if (!StringUtils.hasText(batch.getBillNo())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次缺少退库单号，无法提交审核");
        }

        KingdeeSyncResult result;
        try {
            KingdeeReceiveBillVo kdBill = noticeBillProviderRegistry.require(NoticeBillType.PRODUCTION_RET_STOCK)
                    .getBill(batch.getBillNo().trim());
            Long billId = kdBill != null ? kdBill.getBillId() : null;
            result = kingdeeCloudService.submitAndAuditExistingBill(
                    kingdeeCloudProperties.getPrdRetStockFormId(),
                    batch.getBillNo().trim(),
                    billId);
        } catch (BusinessException ex) {
            result = KingdeeSyncResult.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .build();
        }

        erpSyncLogService.writeLog(
                ErpSyncLogService.SOURCE_PDA_OUTBOUND,
                batchNo,
                combineWorkflowRequestPayload(result),
                combineWorkflowResponsePayload(result),
                result.isSuccess(),
                result.isSuccess() ? null : result.getMessage(),
                1);

        int lineCount = batch.getLineCount() != null ? batch.getLineCount() : 0;
        if (result.isSuccess()) {
            batch.setErpSyncStatus("SUCCESS");
            batch.setErpBillNo(StringUtils.hasText(result.getBillNo()) ? result.getBillNo() : batch.getBillNo());
            batch.setErpSyncMessage(result.isAudited()
                    ? "生产退库提交并审核成功: " + batch.getErpBillNo()
                    : "生产退库同步成功: " + batch.getErpBillNo());
        } else {
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(result.getMessage())));
        }
        batchMapper.updateById(batch);
        return buildSyncResponse(batchNo,
                result.isSuccess() ? lineCount : 0,
                result.isSuccess() ? 0 : Math.max(lineCount, 1),
                batch);
    }

    /**
     * PDA 销售退货：扫已审核退货通知单后 Save 销售退货单并 Submit+Audit。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncSalReturnStockBatch(String batchNo) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        List<PdaInboundRecord> records = recordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSubmitBatchNo, batchNo)
                .eq(PdaInboundRecord::getDeleted, 0));
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次下无退货入库记录");
        }
        List<PdaInboundRecord> pending = records.stream()
                .filter(r -> !"SUCCESS".equals(r.getErpSyncStatus()))
                .collect(Collectors.toList());
        if (pending.isEmpty()) {
            batch.setErpSyncStatus("SUCCESS");
            batchMapper.updateById(batch);
            return buildSyncResponse(batchNo, records.size(), 0, batch);
        }
        for (PdaInboundRecord record : pending) {
            record.setErpSyncStatus("SYNCING");
            record.setUpdateTime(LocalDateTime.now());
            recordMapper.updateById(record);
        }

        KingdeeSyncResult result;
        try {
            result = kingdeeCloudService.syncSalReturnStock(buildSalReturnStockRequest(batch, pending));
        } catch (BusinessException ex) {
            result = KingdeeSyncResult.builder().success(false).message(ex.getMessage()).build();
        }
        int retry = maxRetryCount(pending) + 1;
        erpSyncLogService.writeLog(
                ErpSyncLogService.SOURCE_PDA_INBOUND,
                batchNo,
                combineWorkflowRequestPayload(result),
                combineWorkflowResponsePayload(result),
                result.isSuccess(),
                result.isSuccess() ? null : result.getMessage(),
                retry);

        LocalDateTime syncTime = LocalDateTime.now();
        for (PdaInboundRecord record : pending) {
            record.setErpSyncTime(syncTime);
            record.setErpRetryCount(retry);
            if (result.isSuccess()) {
                record.setErpSyncStatus("SUCCESS");
                record.setErpBillNo(result.getBillNo());
                record.setErpSyncMessage("销售退货同步成功");
            } else {
                record.setErpSyncStatus("FAILED");
                record.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                        KingdeeResponseMessageFormatter.format(result.getMessage())));
            }
            record.setUpdateTime(syncTime);
            recordMapper.updateById(record);
        }
        if (result.isSuccess()) {
            batch.setErpSyncStatus("SUCCESS");
            batch.setErpBillNo(result.getBillNo());
            batch.setErpSyncMessage(StringUtils.hasText(result.getMessage())
                    ? result.getMessage()
                    : ("销售退货保存并审核成功: " + result.getBillNo()));
        } else {
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(result.getMessage())));
            rollbackCreateInboundSubmittedQty(batch, pending);
        }
        batchMapper.updateById(batch);
        long successRecords = records.stream().filter(r -> "SUCCESS".equals(r.getErpSyncStatus())).count();
        int failed = (int) (records.size() - successRecords);
        return buildSyncResponse(batchNo, (int) successRecords, failed, batch);
    }

    private KingdeeSalReturnStockRequest buildSalReturnStockRequest(PdaReceiveSubmitBatch batch,
                                                                     List<PdaInboundRecord> records) {
        String noticeBillNo = batch.getBillNo().trim();
        KingdeeReceiveBillVo notice = noticeBillProviderRegistry.require(NoticeBillType.SALES_RETURN)
                .getBill(noticeBillNo);
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退货通知单不存在或不可用: " + noticeBillNo);
        }
        Map<Integer, KingdeeReceiveBillLineVo> byLineNo = new LinkedHashMap<>();
        Map<Long, KingdeeReceiveBillLineVo> byEntryId = new LinkedHashMap<>();
        if (notice.getLines() != null) {
            for (KingdeeReceiveBillLineVo line : notice.getLines()) {
                if (line == null) {
                    continue;
                }
                if (line.getLineNo() != null) {
                    byLineNo.put(line.getLineNo(), line);
                }
                if (line.getEntryId() != null && line.getEntryId() > 0) {
                    byEntryId.put(line.getEntryId(), line);
                }
            }
        }
        String customer = firstNonBlank(batch.getSupplierCode(), notice.getSupplierCode());
        if (!StringUtils.hasText(customer)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "缺少退货客户编码，请重新打开退货通知单后再提交", "MISSING_CUSTOMER");
        }
        Long noticeBillId = firstNonNull(notice.getBillId(),
                records.stream().map(PdaInboundRecord::getSourceBillId)
                        .filter(id -> id != null && id > 0).findFirst().orElse(null));
        if (noticeBillId == null || noticeBillId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "退货通知单内码(FID)缺失，无法建立关联", "MISSING_RETURN_NOTICE_BILL_ID");
        }

        List<KingdeeSalReturnStockRequest.Line> lines = new ArrayList<>();
        for (PdaInboundRecord record : records) {
            if (record == null || record.getQuantity() == null
                    || record.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            KingdeeReceiveBillLineVo kdLine = null;
            if (record.getSourceEntryId() != null && record.getSourceEntryId() > 0) {
                kdLine = byEntryId.get(record.getSourceEntryId());
            }
            if (kdLine == null && record.getSourceLineNo() != null) {
                kdLine = byLineNo.get(record.getSourceLineNo());
            }
            Long entryId = kdLine != null ? kdLine.getEntryId() : record.getSourceEntryId();
            if (entryId == null || entryId <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "退货通知分录内码缺失，无法下推退货（行 " + record.getSourceLineNo() + "）",
                        "MISSING_RETURN_NOTICE_ENTRY");
            }
            String warehouse = erpWarehouseResolver.resolve(
                    record.getWarehouseCode(),
                    firstNonBlank(record.getErpStockCode(),
                            kdLine != null ? kdLine.getStockWarehouseCode() : null,
                            kingdeeCloudProperties.getSalesOutStockDefaultWarehouseNumber()));
            if (!StringUtils.hasText(warehouse)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "第" + record.getSourceLineNo() + "行缺少仓库编码", "MISSING_WAREHOUSE");
            }
            String lot = BatchNoNormalizer.normalize(firstNonBlank(
                    record.getBatchNo(), kdLine != null ? kdLine.getBatchNo() : null));
            lines.add(KingdeeSalReturnStockRequest.Line.builder()
                    .materialCode(record.getMaterialCode())
                    .materialName(record.getMaterialName())
                    .unitCode(record.getUnitCode())
                    .warehouseCode(warehouse)
                    .locationCode(record.getLocationCode())
                    .batchNo(lot)
                    .quantity(record.getQuantity())
                    .sourceLineNo(record.getSourceLineNo())
                    .sourceEntryId(entryId)
                    .deliveryDate(notice.getBillDate() != null ? notice.getBillDate() : LocalDate.now())
                    .entryNote(record.getBarcodeContent())
                    .build());
        }
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有可同步的销售退货分录");
        }
        return KingdeeSalReturnStockRequest.builder()
                .batchNo(batch.getBatchNo())
                .sourceBillNo(noticeBillNo)
                .sourceBillId(noticeBillId)
                .customerCode(customer)
                .billDate(notice.getBillDate() != null ? notice.getBillDate() : LocalDate.now())
                .note("WMS PDA:" + batch.getBatchNo())
                .lines(lines)
                .build();
    }

    /**
     * PDA 生产退料：回写金蝶实退数量后 Submit + Audit（部分退料同样审核）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncReturnMtrlBatch(String batchNo, List<KingdeeReturnMtrlRequest.Line> lines) {
        return syncExistingReturnActualQtyBatch(
                batchNo,
                NoticeBillType.PRODUCTION_RETURN,
                kingdeeCloudProperties.getReturnMtrlFormId(),
                "生产退料");
    }

    /**
     * PDA 委外退料：回写金蝶实退数量后 Submit + Audit（部分退料同样审核）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncSubReturnMtrlBatch(String batchNo, List<KingdeeSubReturnMtrlRequest.Line> lines) {
        return syncExistingReturnActualQtyBatch(
                batchNo,
                NoticeBillType.OUTSOURCE_RETURN,
                kingdeeCloudProperties.getSubReturnMtrlFormId(),
                "委外退料");
    }

    /**
     * 其他入/出库、采购退料：对已存在的未审核单据 Submit + Audit/WorkflowAudit；
     * 销售发货通知：对已审核单据下推销售出库并审核。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncAuditExistingBillBatch(String batchNo) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        if (!StringUtils.hasText(batch.getBillNo())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次缺少单据号，无法提交审核");
        }
        NoticeBillType billType = NoticeBillType.fromCode(batch.getBillType());
        String formId = resolveAuditFormId(billType);
        if (!StringUtils.hasText(formId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的单据类型: " + batch.getBillType());
        }

        boolean useWorkflowAudit = billType.isWorkflowAuditBill();
        Long kingdeeUserId = null;
        String kingdeeUserName = null;
        if (useWorkflowAudit) {
            SysUserKingdeeMap kingdeeMap = resolveBatchOperatorKingdeeMap(batch);
            kingdeeUserId = kingdeeMap.getKdUserId();
            kingdeeUserName = kingdeeMap.getKdUserNumber();
        }

        KingdeeSyncResult result;
        try {
            if (billType == NoticeBillType.SALES_DELIVERY
                    && kingdeeCloudProperties.isSalesDeliveryPushToOutStock()) {
                KingdeeReceiveBillVo kdBill = noticeBillProviderRegistry.require(billType)
                        .getBill(batch.getBillNo().trim());
                Long billId = kdBill != null ? kdBill.getBillId() : null;
                List<KingdeeSalOutStockEntryFill> fills = buildSalesOutStockEntryFills(batch, kdBill);
                result = kingdeeCloudService.pushSalesDeliveryToOutStockThenAudit(
                        batch.getBillNo().trim(), billId, fills);
            } else {
                result = kingdeeCloudService.submitAndAuditExistingBill(
                        formId, batch.getBillNo().trim(), null,
                        useWorkflowAudit, kingdeeUserId, kingdeeUserName);
            }
        } catch (BusinessException ex) {
            result = KingdeeSyncResult.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .build();
        }

        boolean inbound = billType.getDirection().isInbound();
        erpSyncLogService.writeLog(
                inbound ? ErpSyncLogService.SOURCE_PDA_INBOUND : ErpSyncLogService.SOURCE_PDA_OUTBOUND,
                batchNo,
                combineWorkflowRequestPayload(result),
                combineWorkflowResponsePayload(result),
                result.isSuccess(),
                result.isSuccess() ? null : result.getMessage(),
                1);

        int lineCount = batch.getLineCount() != null ? batch.getLineCount() : 0;
        if (result.isSuccess()) {
            batch.setErpSyncStatus("SUCCESS");
            batch.setErpBillNo(StringUtils.hasText(result.getBillNo()) ? result.getBillNo() : batch.getBillNo());
            String okMsg;
            if (billType == NoticeBillType.SALES_DELIVERY
                    && kingdeeCloudProperties.isSalesDeliveryPushToOutStock()) {
                okMsg = StringUtils.hasText(result.getMessage())
                        ? result.getMessage()
                        : ("销售出库下推并审核成功: " + batch.getErpBillNo());
            } else if (useWorkflowAudit) {
                okMsg = billType.getLabel() + "提交并工作流审批成功: " + batch.getErpBillNo();
            } else {
                okMsg = result.isAudited()
                        ? billType.getLabel() + "提交并审核成功: " + batch.getErpBillNo()
                        : billType.getLabel() + "同步成功: " + batch.getErpBillNo();
            }
            batch.setErpSyncMessage(okMsg);
        } else {
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(result.getMessage())));
        }
        batchMapper.updateById(batch);
        return buildSyncResponse(batchNo,
                result.isSuccess() ? lineCount : 0,
                result.isSuccess() ? 0 : Math.max(lineCount, 1),
                batch);
    }

    private String resolveAuditFormId(NoticeBillType billType) {
        if (billType == null) {
            return null;
        }
        return switch (billType) {
            case OTHER_IN -> kingdeeCloudProperties.getMiscInStockFormId();
            case OTHER_OUT -> kingdeeCloudProperties.getMisDeliveryFormId();
            case SALES_DELIVERY -> billType.getDefaultFormId();
            case PURCHASE_RETURN -> kingdeeCloudProperties.getPurMrbFormId();
            case OUTSOURCE_ISSUE -> kingdeeCloudProperties.getSubPickMtrlFormId();
            case OUTSOURCE_FEED -> kingdeeCloudProperties.getSubFeedMtrlFormId();
            case OUTSOURCE_RETURN -> kingdeeCloudProperties.getSubReturnMtrlFormId();
            case PRODUCTION_ISSUE -> kingdeeCloudProperties.getPickMtrlFormId();
            case PRODUCTION_FEED -> kingdeeCloudProperties.getFeedMtrlFormId();
            case PRODUCTION_RETURN -> kingdeeCloudProperties.getReturnMtrlFormId();
            case PRODUCTION_IN -> kingdeeCloudProperties.getPrdInStockFormId();
            case PRODUCTION_RET_STOCK -> kingdeeCloudProperties.getPrdRetStockFormId();
            default -> null;
        };
    }

    private List<KingdeeReturnMtrlRequest.Line> buildReturnMtrlLinesFromInboundRecords(String batchNo) {
        List<PdaInboundRecord> records = recordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSubmitBatchNo, batchNo)
                .orderByAsc(PdaInboundRecord::getId));
        return records.stream()
                .map(r -> KingdeeReturnMtrlRequest.Line.builder()
                        .materialCode(r.getMaterialCode())
                        .materialName(r.getMaterialName())
                        .unitCode(r.getUnitCode())
                        .warehouseCode(firstNonBlank(r.getErpStockCode(), r.getWarehouseCode()))
                        .locationCode(r.getLocationCode())
                        .batchNo(r.getBatchNo())
                        .quantity(r.getQuantity())
                        .sourceLineNo(r.getSourceLineNo())
                        .entryNote(r.getBarcodeContent())
                        .pickBillId(r.getSourceBillId())
                        .pickEntryId(r.getSourceEntryId())
                        .build())
                .collect(Collectors.toList());
    }

    private List<KingdeeSubReturnMtrlRequest.Line> buildSubReturnMtrlLinesFromInboundRecords(String batchNo) {
        List<PdaInboundRecord> records = recordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSubmitBatchNo, batchNo)
                .orderByAsc(PdaInboundRecord::getId));
        return records.stream()
                .map(r -> KingdeeSubReturnMtrlRequest.Line.builder()
                        .materialCode(r.getMaterialCode())
                        .materialName(r.getMaterialName())
                        .unitCode(r.getUnitCode())
                        .warehouseCode(firstNonBlank(r.getErpStockCode(), r.getWarehouseCode()))
                        .locationCode(r.getLocationCode())
                        .batchNo(r.getBatchNo())
                        .quantity(r.getQuantity())
                        .sourceLineNo(r.getSourceLineNo())
                        .entryNote(r.getBarcodeContent())
                        .pickBillId(r.getSourceBillId())
                        .pickEntryId(r.getSourceEntryId())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncOutboundBatchToErp(String batchNo) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch != null && "OUTSOURCE_ISSUE".equalsIgnoreCase(batch.getBillType())) {
            return syncSubPickMtrlBatch(batchNo, null);
        }
        return syncPickMtrlBatch(batchNo, null);
    }

    private List<KingdeePickMtrlRequest.Line> buildPickMtrlLinesFromTransactions(PdaReceiveSubmitBatch batch) {
        LocalDateTime from = batch.getSubmitTime() != null
                ? batch.getSubmitTime().minusMinutes(2)
                : LocalDateTime.now().minusDays(1);
        LocalDateTime to = batch.getSubmitTime() != null
                ? batch.getSubmitTime().plusMinutes(10)
                : LocalDateTime.now();
        List<InventoryTransaction> txs = inventoryTransactionMapper.selectList(new LambdaQueryWrapper<InventoryTransaction>()
                .eq(InventoryTransaction::getSourceOrderNo, batch.getBillNo())
                .ge(InventoryTransaction::getOperationTime, from)
                .le(InventoryTransaction::getOperationTime, to)
                .orderByAsc(InventoryTransaction::getId));
        return txs.stream()
                .map(tx -> KingdeePickMtrlRequest.Line.builder()
                        .materialCode(tx.getMaterialCode())
                        .unitCode(null)
                        .warehouseCode(erpWarehouseResolver.resolve(
                                tx.getWarehouseCode(),
                                firstNonBlank(tx.getWarehouseCode(),
                                        kingdeeCloudProperties.getStockInDefaultWarehouseNumber())))
                        .locationCode(tx.getLocationCode())
                        .batchNo(tx.getBatchNo())
                        .quantity(tx.getTransactionQty() == null ? BigDecimal.ZERO : tx.getTransactionQty().abs())
                        .sourceLineNo(tx.getSourceOrderLine())
                        .entryNote(tx.getRemark())
                        .build())
                .collect(Collectors.toList());
    }

    private List<KingdeeSubPickMtrlRequest.Line> buildSubPickMtrlLinesFromTransactions(PdaReceiveSubmitBatch batch) {
        LocalDateTime from = batch.getSubmitTime() != null
                ? batch.getSubmitTime().minusMinutes(2)
                : LocalDateTime.now().minusDays(1);
        LocalDateTime to = batch.getSubmitTime() != null
                ? batch.getSubmitTime().plusMinutes(10)
                : LocalDateTime.now();
        List<InventoryTransaction> txs = inventoryTransactionMapper.selectList(new LambdaQueryWrapper<InventoryTransaction>()
                .eq(InventoryTransaction::getSourceOrderNo, batch.getBillNo())
                .ge(InventoryTransaction::getOperationTime, from)
                .le(InventoryTransaction::getOperationTime, to)
                .orderByAsc(InventoryTransaction::getId));
        return txs.stream()
                .map(tx -> KingdeeSubPickMtrlRequest.Line.builder()
                        .materialCode(tx.getMaterialCode())
                        .unitCode(null)
                        .warehouseCode(erpWarehouseResolver.resolve(
                                tx.getWarehouseCode(),
                                firstNonBlank(tx.getWarehouseCode(),
                                        kingdeeCloudProperties.getStockInDefaultWarehouseNumber())))
                        .locationCode(tx.getLocationCode())
                        .batchNo(tx.getBatchNo())
                        .quantity(tx.getTransactionQty() == null ? BigDecimal.ZERO : tx.getTransactionQty().abs())
                        .sourceLineNo(tx.getSourceOrderLine())
                        .entryNote(tx.getRemark())
                        .ppBomBillNo(batch.getBillNo())
                        .build())
                .collect(Collectors.toList());
    }

    private KingdeePurchaseInStockRequest buildPurchaseRequest(PdaReceiveSubmitBatch batch,
                                                                 List<PdaInboundRecord> records) {
        // 整单只 View 一次，确保收料通知单 F_QVHU_Text_qtr 带到采购入库
        KingdeeReceiveBillVo kdBill = receiveBillSourceResolver.loadBill(batch.getBillNo());
        KingdeeReceiveBillSourceResolver.SourceContext billSource =
                KingdeeReceiveBillSourceResolver.toHeaderContext(kdBill);
        if (billSource.getBillId() == null || billSource.getBillId() <= 0) {
            billSource = receiveBillSourceResolver.resolve(batch.getBillNo(), null, null);
        }
        String supplierCode = firstNonBlank(
                batch.getSupplierCode(),
                billSource.getSupplierCode(),
                records.stream().map(PdaInboundRecord::getSupplierCode).filter(StringUtils::hasText).findFirst().orElse(null));
        Long batchSourceBillId = firstNonNull(
                billSource.getBillId(),
                records.stream().map(PdaInboundRecord::getSourceBillId).filter(id -> id != null && id > 0).findFirst().orElse(null));
        if (!StringUtils.hasText(supplierCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "缺少金蝶供应商编码，请重新进入收料通知单详情后再提交");
        }
        if (batchSourceBillId == null || batchSourceBillId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "缺少收料通知单内码(FID)，无法保存采购入库单，请重新打开收料单");
        }

        final Long resolvedBillId = batchSourceBillId;
        final String headerSendBillNo = firstNonBlank(billSource.getSendBillNo());
        final boolean outsourceReceive = isOutsourceReceive(billSource.getBusinessType(), kdBill);
        List<KingdeePurchaseInStockRequest.Line> lines = records.stream()
                .map(r -> {
                    KingdeeReceiveBillSourceResolver.SourceContext lineSource =
                            resolveLineSource(kdBill, batch.getBillNo(), r);
                    String materialCode = firstNonBlank(r.getMaterialCode(), lineSource.getMaterialCode());
                    String erpStock = erpWarehouseResolver.resolve(
                            r.getWarehouseCode(),
                            firstNonBlank(r.getErpStockCode(), lineSource.getStockWarehouseCode()));
                    if (!StringUtils.hasText(materialCode)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "入库记录缺少物料编码: " + r.getRecordNo());
                    }
                    if (!StringUtils.hasText(erpStock)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "入库记录缺少金蝶仓库: " + r.getRecordNo());
                    }
                    Long sourceBillId = firstNonNull(r.getSourceBillId(), resolvedBillId, lineSource.getBillId());
                    Long sourceEntryId = firstNonNull(r.getSourceEntryId(), lineSource.getEntryId());
                    if (sourceBillId == null || sourceBillId <= 0 || sourceEntryId == null || sourceEntryId <= 0) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "缺少收料分录内码，无法保存采购入库单: " + r.getRecordNo()
                                        + " 物料=" + materialCode,
                                "MISSING_SOURCE_ENTRY");
                    }
                    String poOrderNo = firstNonBlank(lineSource.getPoOrderNo(), lineSource.getSubReqBillNo());
                    Long poOrderEntryId = lineSource.getPoOrderEntryId();
                    if (!outsourceReceive
                            && (!StringUtils.hasText(poOrderNo) || poOrderEntryId == null || poOrderEntryId <= 0)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "缺少采购订单关联(订单单号/分录内码)，无法保存采购入库单: " + r.getRecordNo()
                                        + " 物料=" + materialCode
                                        + "，请重新打开收料通知单后再同步",
                                "MISSING_PO_LINK");
                    }
                    String sendBillNo = firstNonBlank(
                            lineSource.getSendBillNo(),
                            headerSendBillNo,
                            batch.getBillNo());
                    if (!StringUtils.hasText(lineSource.getSendBillNo()) && !StringUtils.hasText(headerSendBillNo)) {
                        log.warn("收料通知单未取到 F_QVHU_Text_qtr，采购入库送货单号将回退为收料单号 billNo={} recordNo={}",
                                batch.getBillNo(), r.getRecordNo());
                    }
                    // 库存单位必须用收料单 FUnitID（与基本单位固定换算）；计价单位单独传
                    String stockUnit = firstNonBlank(lineSource.getUnitCode(), r.getUnitCode());
                    String priceUnit = firstNonBlank(
                            lineSource.getPriceUnitCode(), r.getAuxUnitCode(), stockUnit);
                    String kingdeeLot = resolveKingdeeLot(r.getBatchNo(), lineSource.getBatchNo());
                    KingdeeInStockEntryLink sourceLink = KingdeeInStockEntryLink.fromReceiveLine(
                            kingdeeCloudProperties,
                            sourceBillId,
                            sourceEntryId,
                            r.getQuantity(),
                            firstPositive(
                                    r.getSourceRemainInStockBaseQtyOld(),
                                    lineSource.getRemainInStockBaseQty(),
                                    r.getQuantity()),
                            firstPositive(
                                    r.getSourceBaseUnitQtyOld(),
                                    lineSource.getBaseUnitQty(),
                                    r.getQuantity()));
                    return KingdeePurchaseInStockRequest.Line.builder()
                            .materialCode(materialCode)
                            .materialName(r.getMaterialName())
                            .unitCode(stockUnit)
                            .priceUnitCode(priceUnit)
                            .warehouseCode(erpStock)
                            .locationCode(r.getLocationCode())
                            .batchNo(kingdeeLot)
                            .quantity(r.getQuantity())
                            .priceUnitQty(r.getAuxQuantity() != null && r.getAuxQuantity().compareTo(BigDecimal.ZERO) > 0
                                    ? r.getAuxQuantity() : r.getQuantity())
                            .sourceLineNo(r.getSourceLineNo() != null ? r.getSourceLineNo() : lineSource.getEntryLineNo())
                            .sourceBillId(sourceBillId)
                            .sourceEntryId(sourceEntryId)
                            .sourceLink(sourceLink)
                            .sendBillNo(sendBillNo)
                            .poOrderNo(poOrderNo)
                            .poOrderEntryId(poOrderEntryId)
                            .build();
                })
                .collect(Collectors.toList());

        String businessType = outsourceReceive
                ? firstNonBlank(billSource.getBusinessType(), kingdeeCloudProperties.getStockInWwBusinessType(), "WW")
                : firstNonBlank(billSource.getBusinessType(), kingdeeCloudProperties.getStockInBusinessType(), "CG");
        String inStockBillType = outsourceReceive
                ? firstNonBlank(kingdeeCloudProperties.getStockInWwBillTypeNumber(),
                kingdeeCloudProperties.getStockInBillTypeNumber())
                : kingdeeCloudProperties.getStockInBillTypeNumber();
        log.info("Build purchase in-stock from receive bill billNo={} businessType={} billType={} headerSendBillNo={} lines={}",
                batch.getBillNo(), businessType, inStockBillType, headerSendBillNo, lines.size());
        return KingdeePurchaseInStockRequest.builder()
                .batchNo(batch.getBatchNo())
                .sourceBillNo(batch.getBillNo())
                .sourceBillType("PUR_ReceiveBill")
                .businessType(businessType)
                .billTypeNumber(inStockBillType)
                .supplierCode(supplierCode)
                .billDate(LocalDate.now())
                .lines(lines)
                .build();
    }

    private boolean isOutsourceReceive(String businessType, KingdeeReceiveBillVo kdBill) {
        String ww = firstNonBlank(kingdeeCloudProperties.getStockInWwBusinessType(), "WW");
        if (StringUtils.hasText(businessType) && ww.equalsIgnoreCase(businessType.trim())) {
            return true;
        }
        if (kdBill != null && StringUtils.hasText(kdBill.getBusinessType())
                && ww.equalsIgnoreCase(kdBill.getBusinessType().trim())) {
            return true;
        }
        if (kdBill != null && StringUtils.hasText(kdBill.getBillTypeNumber())) {
            String bt = kdBill.getBillTypeNumber().trim().toUpperCase();
            // 常见委外收料单据类型编码含 WW / SUB
            return bt.contains("WW") || bt.contains("SUB");
        }
        return false;
    }

    private KingdeeReceiveBillSourceResolver.SourceContext resolveLineSource(
            KingdeeReceiveBillVo kdBill, String billNo, PdaInboundRecord record) {
        if (kdBill != null && kdBill.getLines() != null) {
            Integer lineNo = record.getSourceLineNo();
            String materialCode = record.getMaterialCode();
            for (KingdeeReceiveBillLineVo line : kdBill.getLines()) {
                boolean lineMatch = lineNo != null && lineNo.equals(line.getLineNo());
                boolean materialMatch = StringUtils.hasText(materialCode)
                        && materialCode.equalsIgnoreCase(line.getMaterialCode());
                if (lineMatch || materialMatch) {
                    return KingdeeReceiveBillSourceResolver.toLineContext(kdBill, line);
                }
            }
            return KingdeeReceiveBillSourceResolver.toHeaderContext(kdBill);
        }
        return receiveBillSourceResolver.resolve(billNo, record.getSourceLineNo(), record.getMaterialCode());
    }

    private String resolveKingdeeLot(String wmsBatchNo, String receiveBillBatchNo) {
        String lot = BatchNoNormalizer.normalize(wmsBatchNo);
        if (StringUtils.hasText(lot)) {
            return lot;
        }
        lot = BatchNoNormalizer.normalize(receiveBillBatchNo);
        if (StringUtils.hasText(lot)) {
            return lot;
        }
        return LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private BigDecimal firstPositive(BigDecimal... values) {
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

    private List<KingdeeSalOutStockEntryFill> buildSalesOutStockEntryFills(PdaReceiveSubmitBatch batch,
                                                                            KingdeeReceiveBillVo kdBill) {
        List<PdaReceiveScanLine> scanLines = loadScanLines(NoticeBillType.SALES_DELIVERY, batch.getBillNo().trim());
        Map<Integer, KingdeeReceiveBillLineVo> kdByLineNo = new HashMap<>();
        Map<String, KingdeeReceiveBillLineVo> kdByMaterial = new HashMap<>();
        if (kdBill != null && kdBill.getLines() != null) {
            for (KingdeeReceiveBillLineVo kdLine : kdBill.getLines()) {
                if (kdLine == null) {
                    continue;
                }
                if (kdLine.getLineNo() != null) {
                    kdByLineNo.put(kdLine.getLineNo(), kdLine);
                }
                if (StringUtils.hasText(kdLine.getMaterialCode())) {
                    kdByMaterial.putIfAbsent(kdLine.getMaterialCode().trim().toUpperCase(), kdLine);
                }
            }
        }
        String defaultStock = firstNonBlank(
                kingdeeCloudProperties.getSalesOutStockDefaultWarehouseNumber(),
                kingdeeCloudProperties.getStockInDefaultWarehouseNumber(),
                "CK004");
        List<KingdeeSalOutStockEntryFill> fills = new ArrayList<>();
        List<String> missingStock = new ArrayList<>();
        List<String> missingLot = new ArrayList<>();
        for (PdaReceiveScanLine scan : scanLines) {
            if (scan == null) {
                continue;
            }
            BigDecimal submitted = scan.getSubmittedQty() == null ? BigDecimal.ZERO : scan.getSubmittedQty();
            if (submitted.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            KingdeeReceiveBillLineVo kdLine = null;
            if (scan.getLineNo() != null) {
                kdLine = kdByLineNo.get(scan.getLineNo());
            }
            if (kdLine == null && StringUtils.hasText(scan.getMaterialCode())) {
                kdLine = kdByMaterial.get(scan.getMaterialCode().trim().toUpperCase());
            }
            String lot = BatchNoNormalizer.normalize(firstNonBlank(
                    scan.getBatchNo(),
                    kdLine != null ? kdLine.getBatchNo() : null));
            String stock = erpWarehouseResolver.resolve(
                    null,
                    firstNonBlank(
                            scan.getErpStockCode(),
                            kdLine != null ? kdLine.getStockWarehouseCode() : null,
                            kdBill != null ? kdBill.getWarehouseCode() : null,
                            defaultStock));
            String material = firstNonBlank(scan.getMaterialCode(),
                    kdLine != null ? kdLine.getMaterialCode() : null);
            if (!StringUtils.hasText(stock)) {
                missingStock.add(StringUtils.hasText(material) ? material : ("行" + scan.getLineNo()));
            }
            if (!StringUtils.hasText(lot)) {
                missingLot.add(StringUtils.hasText(material) ? material : ("行" + scan.getLineNo()));
            }
            fills.add(KingdeeSalOutStockEntryFill.builder()
                    .sourceEntryId(kdLine != null ? kdLine.getEntryId() : null)
                    .materialCode(material)
                    .lineNo(scan.getLineNo())
                    .lotNumber(lot)
                    .stockNumber(stock)
                    .realQty(submitted)
                    .build());
        }
        if (fills.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有已确认的发货数量可下推销售出库");
        }
        if (!missingStock.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "以下物料仓库为空，请指定出货仓库后再提交: " + String.join("、", missingStock));
        }
        // 启用批号的物料金蝶必录 FLot；无批号时提前失败，避免下推后再报错
        if (!missingLot.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "以下物料批号为空（启用批号时必录），请扫码带出批号后再提交: " + String.join("、", missingLot));
        }
        return fills;
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

    private int maxRetryCount(List<PdaInboundRecord> records) {
        return records.stream()
                .map(PdaInboundRecord::getErpRetryCount)
                .filter(c -> c != null && c > 0)
                .max(Integer::compareTo)
                .orElse(0);
    }

    /**
     * 已有未审核领料/补料单：按累计已领数量回写金蝶实发，并立即审核（含部分领料）。
     * 生产/委外补料额外走 WorkflowAudit，且必须绑定金蝶审批人 UserId。
     */
    private Map<String, Object> syncExistingPickActualQtyBatch(String batchNo,
                                                               NoticeBillType billType,
                                                               String formId,
                                                               boolean useWorkflowAudit,
                                                               String bizLabel) {
        PdaReceiveSubmitBatch batch = requireBatch(batchNo, bizLabel);
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        String billNo = batch.getBillNo().trim();
        KingdeeReceiveBillVo kdBill = requireKingdeeBill(billType, billNo, bizLabel);
        if (kdBill.getBillId() == null || kdBill.getBillId() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    bizLabel + "缺少金蝶单据内码(FID)，无法回写实发", "MISSING_BILL_ID");
        }
        List<PdaReceiveScanLine> scanLines = loadScanLines(billType, billNo);
        List<KingdeePickMtrlActualQtyBuilder.Line> qtyLines = buildPickActualQtyLines(kdBill, scanLines);
        if (qtyLines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "未找到可回写的" + bizLabel + "分录（缺少金蝶分录内码FEntryID），请重新进入单据刷新后重试");
        }
        boolean hasIssued = qtyLines.stream()
                .anyMatch(l -> l.getActualQty() != null && l.getActualQty().compareTo(BigDecimal.ZERO) > 0);
        if (!hasIssued) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有已确认的" + bizLabel + "数量可回写");
        }

        Long kingdeeUserId = null;
        String kingdeeUserName = null;
        if (useWorkflowAudit) {
            SysUserKingdeeMap approverMap = resolveBatchOperatorKingdeeMap(batch);
            kingdeeUserId = approverMap.getKdUserId();
            kingdeeUserName = approverMap.getKdUserNumber();
        }

        // 分批领料：未领满只回写实发，领满后再审核，避免首批审核后无法继续改数
        boolean auditAfterSave = isScanQtyFullySubmitted(scanLines);
        KingdeeSyncResult result;
        try {
            result = kingdeeCloudService.savePickMtrlActualQtyThenAudit(
                    formId, kdBill.getBillId(), billNo, qtyLines,
                    useWorkflowAudit, kingdeeUserId, kingdeeUserName, auditAfterSave);
        } catch (BusinessException ex) {
            result = KingdeeSyncResult.builder().success(false).message(ex.getMessage()).build();
        }
        String qtyLabel = useWorkflowAudit ? bizLabel + "实发(工作流)" : bizLabel + "实发";
        if (!auditAfterSave && result != null && result.isSuccess()) {
            qtyLabel = qtyLabel + "(已回写未审核)";
        }
        return finishQtyAuditSync(batch, batchNo, result, qtyLines.size(), qtyLabel,
                ErpSyncLogService.SOURCE_PDA_OUTBOUND);
    }

    /**
     * 已有未审核退料单：按累计已退数量回写金蝶实退，有数量即 Submit+Audit（含部分退料）。
     */
    private Map<String, Object> syncExistingReturnActualQtyBatch(String batchNo,
                                                                 NoticeBillType billType,
                                                                 String formId,
                                                                 String bizLabel) {
        PdaReceiveSubmitBatch batch = requireBatch(batchNo, bizLabel);
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        String billNo = batch.getBillNo().trim();
        KingdeeReceiveBillVo kdBill = requireKingdeeBill(billType, billNo, bizLabel);
        List<PdaReceiveScanLine> scanLines = loadScanLines(billType, billNo);
        List<KingdeeReturnMtrlActualQtyBuilder.Line> qtyLines = buildReturnActualQtyLines(kdBill, scanLines);
        if (qtyLines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "未找到可回写的" + bizLabel + "分录（缺少金蝶分录内码）");
        }
        boolean hasReturned = qtyLines.stream()
                .anyMatch(l -> l.getActualQty() != null && l.getActualQty().compareTo(BigDecimal.ZERO) > 0);
        if (!hasReturned) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有已确认的" + bizLabel + "数量可回写");
        }

        // 部分退料同样审核：有实退数量即 Submit+Audit（与生产/委外退料接口注释一致）
        boolean auditAfterSave = true;
        KingdeeSyncResult result;
        try {
            result = kingdeeCloudService.saveReturnMtrlActualQtyThenAudit(
                    formId, kdBill.getBillId(), billNo, qtyLines, auditAfterSave);
        } catch (BusinessException ex) {
            result = KingdeeSyncResult.builder().success(false).message(ex.getMessage()).build();
        }
        return finishQtyAuditSync(batch, batchNo, result, qtyLines.size(), bizLabel + "实退",
                ErpSyncLogService.SOURCE_PDA_INBOUND);
    }

    /**
     * 扫码行计划量是否已全部提交（用于决定是否对金蝶单据审核）。
     */
    private boolean isScanQtyFullySubmitted(List<PdaReceiveScanLine> scanLines) {
        if (scanLines == null || scanLines.isEmpty()) {
            return false;
        }
        boolean hasPlan = false;
        for (PdaReceiveScanLine line : scanLines) {
            if (line == null) {
                continue;
            }
            BigDecimal plan = line.getPlanQty() != null ? line.getPlanQty() : BigDecimal.ZERO;
            if (plan.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            hasPlan = true;
            BigDecimal submitted = line.getSubmittedQty() != null ? line.getSubmittedQty() : BigDecimal.ZERO;
            if (submitted.compareTo(plan) < 0) {
                return false;
            }
        }
        return hasPlan;
    }

    private PdaReceiveSubmitBatch requireBatch(String batchNo, String bizLabel) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if (!StringUtils.hasText(batch.getBillNo())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次缺少" + bizLabel + "单号，无法同步金蝶");
        }
        return batch;
    }

    private KingdeeReceiveBillVo requireKingdeeBill(NoticeBillType billType, String billNo, String bizLabel) {
        KingdeeReceiveBillVo kdBill = noticeBillProviderRegistry.require(billType).getBill(billNo);
        if (kdBill == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, bizLabel + "单不存在或已完结: " + billNo);
        }
        return kdBill;
    }

    private List<PdaReceiveScanLine> loadScanLines(NoticeBillType billType, String billNo) {
        return scanLineMapper.selectList(new LambdaQueryWrapper<PdaReceiveScanLine>()
                .eq(PdaReceiveScanLine::getBillType, billType.getCode())
                .eq(PdaReceiveScanLine::getDirection, billType.getDirection().name())
                .eq(PdaReceiveScanLine::getBillNo, billNo));
    }

    private Map<String, Object> finishQtyAuditSync(PdaReceiveSubmitBatch batch, String batchNo,
                                                   KingdeeSyncResult result, int qtyLineCount,
                                                   String qtyLabel, String logSource) {
        erpSyncLogService.writeLog(
                logSource,
                batchNo,
                combineWorkflowRequestPayload(result),
                combineWorkflowResponsePayload(result),
                result != null && result.isSuccess(),
                result != null && result.isSuccess() ? null : (result != null ? result.getMessage() : "同步失败"),
                1);

        int lineCount = batch.getLineCount() != null ? batch.getLineCount() : qtyLineCount;
        if (result != null && result.isSuccess()) {
            batch.setErpSyncStatus("SUCCESS");
            batch.setErpBillNo(StringUtils.hasText(result.getBillNo()) ? result.getBillNo() : batch.getBillNo());
            String syncMsg = StringUtils.hasText(result.getMessage())
                    ? result.getMessage()
                    : (qtyLabel + "已回写并审核成功: " + batch.getErpBillNo());
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(syncMsg));
        } else {
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(
                            result != null ? result.getMessage() : "同步失败")));
        }
        batchMapper.updateById(batch);
        return buildSyncResponse(batchNo,
                result != null && result.isSuccess() ? lineCount : 0,
                result != null && result.isSuccess() ? 0 : Math.max(lineCount, 1),
                batch);
    }

    /**
     * 按金蝶全部分录回写：已确认行为累计已领量，未确认行为 0（部分领料审核时未领行实发为 0）。
     */
    private List<KingdeePickMtrlActualQtyBuilder.Line> buildPickActualQtyLines(KingdeeReceiveBillVo kdBill,
                                                                               List<PdaReceiveScanLine> scanLines) {
        Map<Integer, BigDecimal> submittedByLineNo = toSubmittedByLineNo(scanLines);
        Map<String, BigDecimal> submittedByMaterial = toSubmittedByMaterial(scanLines);
        List<KingdeePickMtrlActualQtyBuilder.Line> qtyLines = new ArrayList<>();
        if (kdBill == null || kdBill.getLines() == null) {
            return qtyLines;
        }
        for (KingdeeReceiveBillLineVo kdLine : kdBill.getLines()) {
            if (kdLine == null || kdLine.getEntryId() == null || kdLine.getEntryId() <= 0) {
                continue;
            }
            BigDecimal submitted = resolveSubmittedQty(kdLine, submittedByLineNo, submittedByMaterial);
            qtyLines.add(new KingdeePickMtrlActualQtyBuilder.Line(kdLine.getEntryId(), submitted));
        }
        return qtyLines;
    }

    private List<KingdeeReturnMtrlActualQtyBuilder.Line> buildReturnActualQtyLines(KingdeeReceiveBillVo kdBill,
                                                                                  List<PdaReceiveScanLine> scanLines) {
        Map<Integer, BigDecimal> submittedByLineNo = toSubmittedByLineNo(scanLines);
        Map<String, BigDecimal> submittedByMaterial = toSubmittedByMaterial(scanLines);
        List<KingdeeReturnMtrlActualQtyBuilder.Line> qtyLines = new ArrayList<>();
        if (kdBill == null || kdBill.getLines() == null) {
            return qtyLines;
        }
        for (KingdeeReceiveBillLineVo kdLine : kdBill.getLines()) {
            if (kdLine == null || kdLine.getEntryId() == null || kdLine.getEntryId() <= 0) {
                continue;
            }
            BigDecimal submitted = resolveSubmittedQty(kdLine, submittedByLineNo, submittedByMaterial);
            qtyLines.add(new KingdeeReturnMtrlActualQtyBuilder.Line(kdLine.getEntryId(), submitted));
        }
        return qtyLines;
    }

    private Map<Integer, BigDecimal> toSubmittedByLineNo(List<PdaReceiveScanLine> scanLines) {
        Map<Integer, BigDecimal> map = new LinkedHashMap<>();
        if (scanLines == null) {
            return map;
        }
        for (PdaReceiveScanLine line : scanLines) {
            if (line == null || line.getLineNo() == null) {
                continue;
            }
            BigDecimal submitted = line.getSubmittedQty() != null ? line.getSubmittedQty() : BigDecimal.ZERO;
            map.put(line.getLineNo(), submitted);
        }
        return map;
    }

    private Map<String, BigDecimal> toSubmittedByMaterial(List<PdaReceiveScanLine> scanLines) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        if (scanLines == null) {
            return map;
        }
        for (PdaReceiveScanLine line : scanLines) {
            if (line == null || !StringUtils.hasText(line.getMaterialCode())) {
                continue;
            }
            String code = line.getMaterialCode().trim();
            BigDecimal submitted = line.getSubmittedQty() != null ? line.getSubmittedQty() : BigDecimal.ZERO;
            map.merge(code, submitted, BigDecimal::add);
        }
        return map;
    }

    private BigDecimal resolveSubmittedQty(KingdeeReceiveBillLineVo kdLine,
                                           Map<Integer, BigDecimal> submittedByLineNo,
                                           Map<String, BigDecimal> submittedByMaterial) {
        if (kdLine.getLineNo() != null && submittedByLineNo.containsKey(kdLine.getLineNo())) {
            return submittedByLineNo.get(kdLine.getLineNo());
        }
        if (StringUtils.hasText(kdLine.getMaterialCode())) {
            BigDecimal byMaterial = submittedByMaterial.get(kdLine.getMaterialCode().trim());
            if (byMaterial != null) {
                return byMaterial;
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 从批次操作人解析金蝶用户映射（供 WorkflowAudit.UserId）。
     */
    private SysUserKingdeeMap resolveBatchOperatorKingdeeMap(PdaReceiveSubmitBatch batch) {
        if (batch == null || !StringUtils.hasText(batch.getOperatorId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "批次缺少操作人，无法进行金蝶工作流审批", "OPERATOR_REQUIRED");
        }
        return userKingdeeMapService.requireBoundForWorkflow(batch.getOperatorId().trim());
    }

    private Map<String, Object> buildSyncResponse(String batchNo, int success, int failed, PdaReceiveSubmitBatch batch) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("batchNo", batchNo);
        resp.put("successCount", success);
        resp.put("failedCount", failed);
        resp.put("erpSyncStatus", batch.getErpSyncStatus());
        resp.put("erpBillNo", batch.getErpBillNo());
        resp.put("erpSyncMessage", batch.getErpSyncMessage());
        return resp;
    }

    private String combineWorkflowRequestPayload(KingdeeSyncResult result) {
        return appendPayloadSection(
                appendPayloadSection(result.getRequestJson(), "SUBMIT", result.getSubmitRequestJson()),
                "AUDIT",
                result.getAuditRequestJson());
    }

    private String combineWorkflowResponsePayload(KingdeeSyncResult result) {
        return appendPayloadSection(
                appendPayloadSection(result.getResponseJson(), "SUBMIT", result.getSubmitResponseJson()),
                "AUDIT",
                result.getAuditResponseJson());
    }

    private String appendPayloadSection(String base, String label, String section) {
        if (!StringUtils.hasText(section)) {
            return base;
        }
        String prefix = base != null ? base : "";
        return prefix + "\n---" + label + "---\n" + section;
    }
}
