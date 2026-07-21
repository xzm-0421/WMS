package com.wms.pdainbound.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.auth.security.LoginUser;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.integration.kingdee.KingdeeErpWarehouseResolver;
import com.wms.integration.kingdee.KingdeePurchaseInStockRequest;
import com.wms.integration.kingdee.KingdeeReceiveBillSourceResolver;
import com.wms.integration.kingdee.KingdeeStockInRequest;
import com.wms.integration.kingdee.KingdeeSyncResult;
import com.wms.integration.service.ErpSyncLogService;
import com.wms.pdareceive.service.PdaReceiveSubmitBatchService;
import com.wms.barcode.service.BarcodeTraceLinkService;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.dto.InventoryChangeResult;
import com.wms.inventory.service.InventoryService;
import com.wms.mobile.dto.MobileScanRecognizeRequest;
import com.wms.mobile.service.MobileScanService;
import com.wms.pdainbound.dto.PdaInboundRecordVo;
import com.wms.pdainbound.dto.PdaInboundSubmitRequest;
import com.wms.pdainbound.entity.PdaInboundRecord;
import com.wms.pdainbound.mapper.PdaInboundRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PdaInboundRecordService {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private final PdaInboundRecordMapper recordMapper;
    private final BaseMaterialMapper materialMapper;
    private final InventoryService inventoryService;
    private final MobileScanService mobileScanService;
    private final KingdeeCloudService kingdeeCloudService;
    private final ErpSyncLogService erpSyncLogService;
    private final BarcodeTraceLinkService barcodeTraceLinkService;
    private final PdaReceiveSubmitBatchService receiveSubmitBatchService;
    private final KingdeeErpWarehouseResolver erpWarehouseResolver;
    private final KingdeeReceiveBillSourceResolver receiveBillSourceResolver;

    @Transactional(rollbackFor = Exception.class)
    public PdaInboundRecordVo submit(PdaInboundSubmitRequest req) {
        LoginUser user = SecurityUtils.currentUser();

        String materialCode = req.getMaterialCode();
        String batchNo = req.getBatchNo();
        if (!StringUtils.hasText(materialCode)) {
            MobileScanRecognizeRequest recognizeReq = new MobileScanRecognizeRequest();
            recognizeReq.setBarcodeContent(req.getBarcodeContent());
            recognizeReq.setWarehouseCode(req.getWarehouseCode());
            Map<String, Object> recognized = mobileScanService.recognize(recognizeReq);
            materialCode = (String) recognized.get("materialCode");
            if (!StringUtils.hasText(batchNo)) {
                batchNo = (String) recognized.get("batchNo");
            }
        }
        if (!StringUtils.hasText(materialCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法识别物料编码，请检查条码");
        }

        if (!StringUtils.hasText(batchNo) && StringUtils.hasText(req.getBarcodeContent())) {
            MobileScanRecognizeRequest batchReq = new MobileScanRecognizeRequest();
            batchReq.setBarcodeContent(req.getBarcodeContent());
            batchReq.setWarehouseCode(req.getWarehouseCode());
            Map<String, Object> parsed = mobileScanService.recognize(batchReq);
            batchNo = (String) parsed.get("batchNo");
        }

        BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getMaterialCode, materialCode));
        if (material == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在: " + materialCode, "MATERIAL_NOT_FOUND");
        }

        String locationCode = "";

        PdaInboundRecord record = new PdaInboundRecord();
        record.setRecordNo(generateRecordNo());
        record.setMaterialCode(materialCode);
        record.setMaterialName(material.getMaterialName());
        record.setSpecification(material.getSpecification());
        record.setUnitCode(material.getUnitCode());
        record.setWarehouseCode(req.getWarehouseCode());
        record.setLocationCode(locationCode);
        record.setBatchNo(StringUtils.hasText(batchNo) ? batchNo : "DEFAULT");
        record.setQuantity(req.getQuantity());
        record.setBarcodeContent(req.getBarcodeContent());
        record.setOperatorId(String.valueOf(user.getUserId()));
        record.setOperatorName(user.getRealName() != null ? user.getRealName() : user.getUsername());
        record.setDeviceNo(req.getDeviceNo());
        record.setStatus("SUBMITTED");
        record.setErpSyncStatus("PENDING");
        record.setErpRetryCount(0);
        record.setRemark(req.getRemark());
        record.setCreateTime(LocalDateTime.now());
        recordMapper.insert(record);

        InventoryChangeCommand cmd = InventoryChangeCommand.builder()
                .transactionType("PDA_INBOUND")
                .warehouseCode(record.getWarehouseCode())
                .locationCode(record.getLocationCode())
                .materialCode(record.getMaterialCode())
                .batchNo(record.getBatchNo())
                .quantity(record.getQuantity())
                .sourceOrderType("PDA_INBOUND")
                .sourceOrderNo(record.getRecordNo())
                .operatorId(record.getOperatorId())
                .operatorName(record.getOperatorName())
                .deviceNo(record.getDeviceNo())
                .remark(record.getBarcodeContent())
                .build();
        InventoryChangeResult changeResult = inventoryService.increaseWithTransaction(cmd);
        barcodeTraceLinkService.linkPdaInbound(
                record.getBarcodeContent(),
                record.getRecordNo(),
                changeResult.getTransactionNo(),
                record.getMaterialCode(),
                record.getBatchNo(),
                null);

        syncToKingdee(record);
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);

        return toVo(record);
    }

    public PageResult<PdaInboundRecordVo> page(String recordNo, String materialCode, String warehouseCode,
                                                String status, String erpSyncStatus,
                                                long current, long size) {
        LambdaQueryWrapper<PdaInboundRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PdaInboundRecord::getDeleted, 0)
                .like(StringUtils.hasText(recordNo), PdaInboundRecord::getRecordNo, recordNo)
                .like(StringUtils.hasText(materialCode), PdaInboundRecord::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(warehouseCode), PdaInboundRecord::getWarehouseCode, warehouseCode)
                .eq(StringUtils.hasText(status), PdaInboundRecord::getStatus, status)
                .eq(StringUtils.hasText(erpSyncStatus), PdaInboundRecord::getErpSyncStatus, erpSyncStatus)
                .orderByDesc(PdaInboundRecord::getCreateTime);

        Page<PdaInboundRecord> page = recordMapper.selectPage(new Page<>(current, size), wrapper);
        List<PdaInboundRecordVo> vos = page.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PdaInboundRecordVo getByRecordNo(String recordNo) {
        PdaInboundRecord record = requireRecord(recordNo);
        return toVo(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(String recordNo) {
        LoginUser user = SecurityUtils.currentUser();
        PdaInboundRecord record = requireRecord(recordNo);
        if (!"SUBMITTED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已提交记录可审核", "STATUS_CONFLICT");
        }
        record.setStatus("AUDITED");
        record.setAuditorId(String.valueOf(user.getUserId()));
        record.setAuditorName(user.getRealName() != null ? user.getRealName() : user.getUsername());
        record.setAuditTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reverse(String recordNo, String reason) {
        LoginUser user = SecurityUtils.currentUser();
        PdaInboundRecord record = requireRecord(recordNo);
        if ("REVERSED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "记录已冲销", "ALREADY_REVERSED");
        }
        if (!List.of("SUBMITTED", "AUDITED").contains(record.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可冲销", "STATUS_CONFLICT");
        }

        InventoryChangeCommand cmd = InventoryChangeCommand.builder()
                .transactionType("PDA_INBOUND_REVERSE")
                .warehouseCode(record.getWarehouseCode())
                .locationCode(record.getLocationCode())
                .materialCode(record.getMaterialCode())
                .batchNo(record.getBatchNo())
                .quantity(record.getQuantity())
                .sourceOrderType("PDA_INBOUND")
                .sourceOrderNo(record.getRecordNo())
                .operatorId(String.valueOf(user.getUserId()))
                .operatorName(user.getRealName() != null ? user.getRealName() : user.getUsername())
                .remark("冲销: " + (reason != null ? reason : ""))
                .build();
        inventoryService.decrease(cmd);

        record.setStatus("REVERSED");
        record.setReverseBy(user.getRealName() != null ? user.getRealName() : user.getUsername());
        record.setReverseTime(LocalDateTime.now());
        record.setReverseReason(reason);
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public PdaInboundRecordVo resyncErp(String recordNo) {
        PdaInboundRecord record = requireRecord(recordNo);
        if ("REVERSED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已冲销记录不可同步", "STATUS_CONFLICT");
        }
        if ("SUCCESS".equals(record.getErpSyncStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已成功同步，无需重试", "ALREADY_SYNCED");
        }
        if (StringUtils.hasText(record.getSubmitBatchNo())) {
            receiveSubmitBatchService.syncBatchToErp(record.getSubmitBatchNo());
            record = requireRecord(recordNo);
            return toVo(record);
        }
        syncToKingdee(record);
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        return toVo(record);
    }

    public List<com.wms.integration.entity.ErpSyncLog> syncLogs(String recordNo) {
        PdaInboundRecord record = requireRecord(recordNo);
        String sourceNo = StringUtils.hasText(record.getSubmitBatchNo())
                ? record.getSubmitBatchNo() : recordNo;
        return erpSyncLogService.listBySource(ErpSyncLogService.SOURCE_PDA_INBOUND, sourceNo);
    }

    private void syncToKingdee(PdaInboundRecord record) {
        record.setErpSyncStatus("SYNCING");
        KingdeeStockInRequest kdReq = buildKingdeeRequest(record);
        int retry = record.getErpRetryCount() == null ? 0 : record.getErpRetryCount();
        KingdeeSyncResult result = kingdeeCloudService.syncStockIn(kdReq);
        record.setErpSyncTime(LocalDateTime.now());
        record.setErpRetryCount(retry + 1);

        erpSyncLogService.writeLog(
                ErpSyncLogService.SOURCE_PDA_INBOUND,
                record.getRecordNo(),
                result.getRequestJson(),
                result.getResponseJson(),
                result.isSuccess(),
                result.isSuccess() ? null : result.getMessage(),
                retry + 1);

        if (result.isSuccess()) {
            record.setErpSyncStatus("SUCCESS");
            record.setErpBillNo(result.getBillNo());
            record.setErpSyncMessage(ErpSyncLogService.truncateMessage(result.getMessage()));
        } else {
            record.setErpSyncStatus("FAILED");
            record.setErpSyncMessage(ErpSyncLogService.truncateMessage(result.getMessage()));
        }
    }

    private KingdeeStockInRequest buildKingdeeRequest(PdaInboundRecord record) {
        String sourceBillNo = record.getSourceBillNo();
        String supplierCode = firstNonBlank(record.getSupplierCode());
        String erpStock = firstNonBlank(record.getErpStockCode());
        String unitCode = record.getUnitCode();
        Long sourceBillId = record.getSourceBillId();
        Long sourceEntryId = record.getSourceEntryId();
        Integer sourceLineNo = record.getSourceLineNo();

        KingdeeReceiveBillSourceResolver.SourceContext source = null;
        if (!StringUtils.hasText(supplierCode) || !StringUtils.hasText(erpStock)
                || sourceBillId == null || sourceEntryId == null) {
            source = receiveBillSourceResolver.resolve(sourceBillNo, record.getSourceLineNo(), record.getMaterialCode());
            supplierCode = firstNonBlank(supplierCode, source.getSupplierCode());
            erpStock = firstNonBlank(erpStock, source.getStockWarehouseCode());
            unitCode = firstNonBlank(unitCode, source.getUnitCode());
            sourceBillId = firstNonNull(sourceBillId, source.getBillId());
            sourceEntryId = firstNonNull(sourceEntryId, source.getEntryId());
            if (sourceLineNo == null) {
                sourceLineNo = source.getEntryLineNo();
            }
        }
        erpStock = erpWarehouseResolver.resolve(record.getWarehouseCode(), erpStock);
        if (!StringUtils.hasText(supplierCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "入库记录缺少金蝶供应商编码: " + record.getRecordNo());
        }
        if (!StringUtils.hasText(erpStock)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "入库记录缺少金蝶仓库编码: " + record.getRecordNo());
        }
        return KingdeeStockInRequest.builder()
                .recordNo(record.getRecordNo())
                .materialCode(record.getMaterialCode())
                .materialName(record.getMaterialName())
                .warehouseCode(record.getWarehouseCode())
                .erpWarehouseCode(erpStock)
                .locationCode(record.getLocationCode())
                .batchNo(record.getBatchNo())
                .quantity(record.getQuantity())
                .unitCode(unitCode)
                .supplierCode(supplierCode)
                .sourceBillType("PUR_ReceiveBill")
                .sourceBillNo(sourceBillNo)
                .sourceLineNo(sourceLineNo)
                .sourceBillId(sourceBillId)
                .sourceEntryId(sourceEntryId)
                .build();
    }

    private Long firstNonNull(Long primary, Long fallback) {
        return primary != null ? primary : fallback;
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

    private PdaInboundRecord requireRecord(String recordNo) {
        PdaInboundRecord record = recordMapper.selectOne(new LambdaQueryWrapper<PdaInboundRecord>()
                .eq(PdaInboundRecord::getRecordNo, recordNo)
                .eq(PdaInboundRecord::getDeleted, 0));
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入库记录不存在");
        }
        return record;
    }

    private PdaInboundRecordVo toVo(PdaInboundRecord record) {
        PdaInboundRecordVo vo = new PdaInboundRecordVo();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    private String generateRecordNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "PIR" + date + String.format("%04d", SEQ.getAndIncrement() % 10000);
    }
}
