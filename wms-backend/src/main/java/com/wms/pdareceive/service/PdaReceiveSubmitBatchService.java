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
import com.wms.integration.kingdee.KingdeePickMtrlRequest;
import com.wms.integration.kingdee.KingdeePurchaseInStockRequest;
import com.wms.integration.kingdee.KingdeeReceiveBillSourceResolver;
import com.wms.integration.kingdee.KingdeeResponseMessageFormatter;
import com.wms.integration.kingdee.KingdeeReturnMtrlRequest;
import com.wms.integration.kingdee.KingdeeSubPickMtrlRequest;
import com.wms.integration.kingdee.KingdeeSubReturnMtrlRequest;
import com.wms.integration.kingdee.KingdeeSyncResult;
import com.wms.integration.service.ErpSyncLogService;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.mapper.InventoryTransactionMapper;
import com.wms.pdainbound.entity.PdaInboundRecord;
import com.wms.pdainbound.mapper.PdaInboundRecordMapper;
import com.wms.pdareceive.entity.PdaReceiveSubmitBatch;
import com.wms.pdareceive.mapper.PdaReceiveSubmitBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdaReceiveSubmitBatchService {

    private final PdaReceiveSubmitBatchMapper batchMapper;
    private final PdaInboundRecordMapper recordMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeErpWarehouseResolver erpWarehouseResolver;
    private final KingdeeReceiveBillSourceResolver receiveBillSourceResolver;
    private final KingdeeCloudProperties kingdeeCloudProperties;
    private final ErpSyncLogService erpSyncLogService;

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
        if ("PRODUCTION_RETURN".equalsIgnoreCase(batch.getBillType())) {
            return syncReturnMtrlBatch(batchNo, null);
        }
        if ("OUTSOURCE_RETURN".equalsIgnoreCase(batch.getBillType())) {
            return syncSubReturnMtrlBatch(batchNo, null);
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
     * PDA 生产领料出库批次同步金蝶生产领料单（PRD_PickMtrl）。
     * {@code lines} 为空时从库存流水按提交时间窗反查明细（Web 重同步）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncPickMtrlBatch(String batchNo, List<KingdeePickMtrlRequest.Line> lines) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        List<KingdeePickMtrlRequest.Line> syncLines = lines;
        if (syncLines == null || syncLines.isEmpty()) {
            syncLines = buildPickMtrlLinesFromTransactions(batch);
        }
        if (syncLines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次下无可同步出库明细");
        }

        KingdeePickMtrlRequest kdReq = KingdeePickMtrlRequest.builder()
                .batchNo(batch.getBatchNo())
                .sourceBillNo(batch.getBillNo())
                .sourceBillType(batch.getBillType())
                .sourceBillId(syncLines.stream()
                        .map(KingdeePickMtrlRequest.Line::getPpBomBillId)
                        .filter(id -> id != null && id > 0)
                        .findFirst()
                        .orElse(null))
                .workShopCode(batch.getSupplierCode())
                .stockCode(syncLines.get(0).getWarehouseCode())
                .billDate(LocalDate.now())
                .note("WMS PDA 用料清单 " + batch.getBillNo())
                .lines(syncLines)
                .build();

        KingdeeSyncResult result;
        try {
            result = kingdeeCloudService.syncPickMtrl(kdReq);
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

        if (result.isSuccess()) {
            batch.setErpSyncStatus("SUCCESS");
            batch.setErpBillNo(result.getBillNo());
            batch.setErpSyncMessage(result.isAudited()
                    ? "生产领料保存、提交并审核成功: " + result.getBillNo()
                    : "生产领料同步成功: " + result.getBillNo());
        } else {
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(result.getMessage())));
        }
        batchMapper.updateById(batch);
        return buildSyncResponse(batchNo,
                result.isSuccess() ? syncLines.size() : 0,
                result.isSuccess() ? 0 : syncLines.size(),
                batch);
    }

    /**
     * PDA 委外领料出库批次同步金蝶委外领料单（SUB_PickMtrl）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncSubPickMtrlBatch(String batchNo, List<KingdeeSubPickMtrlRequest.Line> lines) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        List<KingdeeSubPickMtrlRequest.Line> syncLines = lines;
        if (syncLines == null || syncLines.isEmpty()) {
            syncLines = buildSubPickMtrlLinesFromTransactions(batch);
        }
        if (syncLines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次下无可同步出库明细");
        }

        KingdeeSubPickMtrlRequest kdReq = KingdeeSubPickMtrlRequest.builder()
                .batchNo(batch.getBatchNo())
                .sourceBillNo(batch.getBillNo())
                .sourceBillType(batch.getBillType())
                .sourceBillId(syncLines.stream()
                        .map(KingdeeSubPickMtrlRequest.Line::getPpBomBillId)
                        .filter(id -> id != null && id > 0)
                        .findFirst()
                        .orElse(null))
                .supplierCode(batch.getSupplierCode())
                .stockCode(syncLines.get(0).getWarehouseCode())
                .billDate(LocalDate.now())
                .note("WMS PDA 委外用料清单 " + batch.getBillNo())
                .lines(syncLines)
                .build();

        KingdeeSyncResult result;
        try {
            result = kingdeeCloudService.syncSubPickMtrl(kdReq);
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

        if (result.isSuccess()) {
            batch.setErpSyncStatus("SUCCESS");
            batch.setErpBillNo(result.getBillNo());
            batch.setErpSyncMessage(result.isAudited()
                    ? "委外领料保存、提交并审核成功: " + result.getBillNo()
                    : "委外领料同步成功: " + result.getBillNo());
        } else {
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(result.getMessage())));
        }
        batchMapper.updateById(batch);
        return buildSyncResponse(batchNo,
                result.isSuccess() ? syncLines.size() : 0,
                result.isSuccess() ? 0 : syncLines.size(),
                batch);
    }

    /**
     * PDA 生产退料入库批次同步金蝶生产退料单（PRD_ReturnMtrl）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncReturnMtrlBatch(String batchNo, List<KingdeeReturnMtrlRequest.Line> lines) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        List<KingdeeReturnMtrlRequest.Line> syncLines = lines;
        if (syncLines == null || syncLines.isEmpty()) {
            syncLines = buildReturnMtrlLinesFromInboundRecords(batchNo);
        }
        if (syncLines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次下无可同步退料明细");
        }

        KingdeeReturnMtrlRequest kdReq = KingdeeReturnMtrlRequest.builder()
                .batchNo(batch.getBatchNo())
                .sourceBillNo(batch.getBillNo())
                .sourceBillType(batch.getBillType())
                .sourceBillId(syncLines.stream()
                        .map(KingdeeReturnMtrlRequest.Line::getPickBillId)
                        .filter(id -> id != null && id > 0)
                        .findFirst()
                        .orElse(null))
                .workShopCode(batch.getSupplierCode())
                .stockCode(syncLines.get(0).getWarehouseCode())
                .billDate(LocalDate.now())
                .note("WMS PDA 领料单退料 " + batch.getBillNo())
                .lines(syncLines)
                .build();

        KingdeeSyncResult result;
        try {
            result = kingdeeCloudService.syncReturnMtrl(kdReq);
        } catch (BusinessException ex) {
            result = KingdeeSyncResult.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .build();
        }

        erpSyncLogService.writeLog(
                ErpSyncLogService.SOURCE_PDA_INBOUND,
                batchNo,
                combineWorkflowRequestPayload(result),
                combineWorkflowResponsePayload(result),
                result.isSuccess(),
                result.isSuccess() ? null : result.getMessage(),
                1);

        LocalDateTime syncTime = LocalDateTime.now();
        List<PdaInboundRecord> records = recordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSubmitBatchNo, batchNo));
        for (PdaInboundRecord record : records) {
            record.setErpRetryCount((record.getErpRetryCount() == null ? 0 : record.getErpRetryCount()) + 1);
            if (result.isSuccess()) {
                record.setErpSyncStatus("SUCCESS");
                record.setErpBillNo(result.getBillNo());
                record.setErpSyncMessage(result.isAudited()
                        ? "生产退料保存、提交并审核成功: " + result.getBillNo()
                        : "生产退料同步成功: " + result.getBillNo());
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
            batch.setErpSyncMessage(result.isAudited()
                    ? "生产退料保存、提交并审核成功: " + result.getBillNo()
                    : "生产退料同步成功: " + result.getBillNo());
        } else {
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(result.getMessage())));
        }
        batchMapper.updateById(batch);
        return buildSyncResponse(batchNo,
                result.isSuccess() ? syncLines.size() : 0,
                result.isSuccess() ? 0 : syncLines.size(),
                batch);
    }

    /**
     * PDA 委外退料入库批次同步金蝶委外退料单（SUB_RETURNMTRL）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncSubReturnMtrlBatch(String batchNo, List<KingdeeSubReturnMtrlRequest.Line> lines) {
        PdaReceiveSubmitBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<PdaReceiveSubmitBatch>()
                .eq(PdaReceiveSubmitBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交批次不存在");
        }
        if ("SUCCESS".equals(batch.getErpSyncStatus()) && StringUtils.hasText(batch.getErpBillNo())) {
            return buildSyncResponse(batchNo, batch.getLineCount() != null ? batch.getLineCount() : 0, 0, batch);
        }
        List<KingdeeSubReturnMtrlRequest.Line> syncLines = lines;
        if (syncLines == null || syncLines.isEmpty()) {
            syncLines = buildSubReturnMtrlLinesFromInboundRecords(batchNo);
        }
        if (syncLines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批次下无可同步退料明细");
        }

        KingdeeSubReturnMtrlRequest kdReq = KingdeeSubReturnMtrlRequest.builder()
                .batchNo(batch.getBatchNo())
                .sourceBillNo(batch.getBillNo())
                .sourceBillType(batch.getBillType())
                .sourceBillId(syncLines.stream()
                        .map(KingdeeSubReturnMtrlRequest.Line::getPickBillId)
                        .filter(id -> id != null && id > 0)
                        .findFirst()
                        .orElse(null))
                .supplierCode(batch.getSupplierCode())
                .stockCode(syncLines.get(0).getWarehouseCode())
                .billDate(LocalDate.now())
                .note("WMS PDA 委外领料单退料 " + batch.getBillNo())
                .lines(syncLines)
                .build();

        KingdeeSyncResult result;
        try {
            result = kingdeeCloudService.syncSubReturnMtrl(kdReq);
        } catch (BusinessException ex) {
            result = KingdeeSyncResult.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .build();
        }

        erpSyncLogService.writeLog(
                ErpSyncLogService.SOURCE_PDA_INBOUND,
                batchNo,
                combineWorkflowRequestPayload(result),
                combineWorkflowResponsePayload(result),
                result.isSuccess(),
                result.isSuccess() ? null : result.getMessage(),
                1);

        LocalDateTime syncTime = LocalDateTime.now();
        List<PdaInboundRecord> records = recordMapper.selectList(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getSubmitBatchNo, batchNo));
        for (PdaInboundRecord record : records) {
            record.setErpRetryCount((record.getErpRetryCount() == null ? 0 : record.getErpRetryCount()) + 1);
            if (result.isSuccess()) {
                record.setErpSyncStatus("SUCCESS");
                record.setErpBillNo(result.getBillNo());
                record.setErpSyncMessage(result.isAudited()
                        ? "委外退料保存、提交并审核成功: " + result.getBillNo()
                        : "委外退料同步成功: " + result.getBillNo());
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
            batch.setErpSyncMessage(result.isAudited()
                    ? "委外退料保存、提交并审核成功: " + result.getBillNo()
                    : "委外退料同步成功: " + result.getBillNo());
        } else {
            batch.setErpSyncStatus("FAILED");
            batch.setErpSyncMessage(ErpSyncLogService.truncateMessage(
                    KingdeeResponseMessageFormatter.format(result.getMessage())));
        }
        batchMapper.updateById(batch);
        return buildSyncResponse(batchNo,
                result.isSuccess() ? syncLines.size() : 0,
                result.isSuccess() ? 0 : syncLines.size(),
                batch);
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
        KingdeeReceiveBillSourceResolver.SourceContext billSource =
                receiveBillSourceResolver.resolve(batch.getBillNo(), null, null);
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
        List<KingdeePurchaseInStockRequest.Line> lines = records.stream()
                .map(r -> {
                    KingdeeReceiveBillSourceResolver.SourceContext lineSource = receiveBillSourceResolver.resolve(
                            batch.getBillNo(), r.getSourceLineNo(), r.getMaterialCode());
                    String materialCode = StringUtils.hasText(r.getMaterialCode()) ? r.getMaterialCode().trim() : null;
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
                    String poOrderNo = firstNonBlank(lineSource.getPoOrderNo());
                    Long poOrderEntryId = lineSource.getPoOrderEntryId();
                    if (!StringUtils.hasText(poOrderNo) || poOrderEntryId == null || poOrderEntryId <= 0) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "缺少采购订单关联(订单单号/分录内码)，无法保存采购入库单: " + r.getRecordNo()
                                        + " 物料=" + materialCode
                                        + "，请重新打开收料通知单后再同步",
                                "MISSING_PO_LINK");
                    }
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
                            .unitCode(firstNonBlank(r.getUnitCode(), lineSource.getUnitCode()))
                            .warehouseCode(erpStock)
                            .locationCode(r.getLocationCode())
                            .batchNo(kingdeeLot)
                            .quantity(r.getQuantity())
                            .sourceLineNo(r.getSourceLineNo() != null ? r.getSourceLineNo() : lineSource.getEntryLineNo())
                            .sourceBillId(sourceBillId)
                            .sourceEntryId(sourceEntryId)
                            .sourceLink(sourceLink)
                            .poOrderNo(poOrderNo)
                            .poOrderEntryId(poOrderEntryId)
                            .build();
                })
                .collect(Collectors.toList());

        return KingdeePurchaseInStockRequest.builder()
                .batchNo(batch.getBatchNo())
                .sourceBillNo(batch.getBillNo())
                .sourceBillType("PUR_ReceiveBill")
                .supplierCode(supplierCode)
                .billDate(LocalDate.now())
                .lines(lines)
                .build();
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
