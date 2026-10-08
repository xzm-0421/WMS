package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.integration.kingdee.KingdeeSyncResult;
import com.wms.mes.MesConstants;
import com.wms.mes.MesProperties;
import com.wms.mes.domain.MesRetryBackoff;
import com.wms.mes.dto.MesSyncPanelVo;
import com.wms.mes.entity.MesDefect;
import com.wms.mes.entity.MesErpOutbox;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesReworkOp;
import com.wms.mes.entity.MesRuntime;
import com.wms.mes.entity.MesTransfer;
import com.wms.mes.kingdee.MesDefectSaveBuilder;
import com.wms.mes.kingdee.MesReportSaveBuilder;
import com.wms.mes.kingdee.MesReworkOrderSaveBuilder;
import com.wms.mes.kingdee.MesTransferSaveBuilder;
import com.wms.mes.mapper.MesDefectMapper;
import com.wms.mes.mapper.MesErpOutboxMapper;
import com.wms.mes.mapper.MesReportMapper;
import com.wms.mes.mapper.MesReworkOpMapper;
import com.wms.mes.mapper.MesTransferMapper;
import com.wms.system.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报工/转移异步回写 ERP：指数退避、失败人工介入、同步面板统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesSyncWorkerService {

    private final MesReportMapper reportMapper;
    private final MesTransferMapper transferMapper;
    private final MesErpOutboxMapper outboxMapper;
    private final MesDefectMapper defectMapper;
    private final MesReworkOpMapper reworkOpMapper;
    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties kingdeeProperties;
    private final MesProperties mesProperties;
    private final MesHealthService healthService;
    private final ObjectMapper objectMapper;
    private final MesEventService mesEventService;
    private final AlertService alertService;

    public void drainQueue() {
        long queueSize = countReport(MesConstants.SYNC_PENDING) + countTransfer(MesConstants.SYNC_PENDING);
        if (queueSize > mesProperties.getQueueCapacity()) {
            // 仅告警：新数据接收已由 MesReportService.assertQueueCapacity 拦截，
            // 此处必须继续回写，否则积压永远无法下降。
            log.error("MES同步队列已满: {} 条记录待同步, 请尽快处理", queueSize);
            sendAlert("MES同步队列已满", String.format("当前队列长度: %d", queueSize));
        }
        if (!kingdeeCloudService.isEnabled() || !healthService.isOnline()) {
            log.debug("MES sync skip: ERP unavailable");
            return;
        }
        processReports();
        processTransfers();
        processOutbox();
        markStale();
    }

    private void sendAlert(String title, String content) {
        alertService.raise(AlertService.TYPE_QUEUE_FULL, "ERROR", title, content, null);
    }

    private void trackSync(boolean success, String bizType, String bizNo, String reason) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("bizType", bizType);
        props.put("success", success);
        if (reason != null) {
            props.put("reason", reason);
        }
        mesEventService.track(success ? MesEventService.SYNC_SUCCESS : MesEventService.SYNC_FAIL,
                bizType, bizNo, props);
    }

    public MesSyncPanelVo panel() {
        MesRuntime runtime = healthService.current();
        MesSyncPanelVo vo = new MesSyncPanelVo();
        vo.setNetworkStatus(runtime.getNetworkStatus());
        vo.setLastCheckTime(runtime.getLastCheckTime());
        vo.setLastError(runtime.getLastError());
        long reportPending = countReport(MesConstants.SYNC_PENDING);
        long reportFailed = countReport(MesConstants.SYNC_FAILED) + countReport(MesConstants.SYNC_MANUAL);
        long transferPending = countTransfer(MesConstants.SYNC_PENDING);
        long transferFailed = countTransfer(MesConstants.SYNC_FAILED) + countTransfer(MesConstants.SYNC_MANUAL);
        vo.setReportPendingCount(reportPending);
        vo.setReportFailedCount(reportFailed);
        vo.setTransferPendingCount(transferPending);
        vo.setTransferFailedCount(transferFailed);
        vo.setPendingCount(reportPending + transferPending);
        vo.setSyncingCount(countReport(MesConstants.SYNC_SYNCING) + countTransfer(MesConstants.SYNC_SYNCING));
        vo.setFailedCount(reportFailed + transferFailed);
        vo.setDefectPendingCount(countOutbox(MesConstants.BIZ_DEFECT, MesConstants.SYNC_PENDING));
        vo.setDefectFailedCount(countOutbox(MesConstants.BIZ_DEFECT, MesConstants.SYNC_FAILED)
                + countOutbox(MesConstants.BIZ_DEFECT, MesConstants.SYNC_MANUAL));
        vo.setReworkPendingCount(countOutbox(MesConstants.BIZ_REWORK, MesConstants.SYNC_PENDING));
        vo.setReworkFailedCount(countOutbox(MesConstants.BIZ_REWORK, MesConstants.SYNC_FAILED)
                + countOutbox(MesConstants.BIZ_REWORK, MesConstants.SYNC_MANUAL));
        vo.setQueueTotal(vo.getPendingCount() + vo.getSyncingCount() + vo.getFailedCount());
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        Long todaySynced = reportMapper.selectCount(new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getSyncStatus, MesConstants.SYNC_SUCCESS)
                .ge(MesReport::getSyncTime, start));
        Long todayTransfer = transferMapper.selectCount(new LambdaQueryWrapper<MesTransfer>()
                .eq(MesTransfer::getSyncStatus, MesConstants.SYNC_SUCCESS)
                .ge(MesTransfer::getSyncTime, start));
        vo.setTodaySyncedCount((todaySynced == null ? 0 : todaySynced) + (todayTransfer == null ? 0 : todayTransfer));
        MesReport lastReport = latestSyncedReport();
        MesTransfer lastTransfer = latestSyncedTransfer();
        LocalDateTime last = null;
        if (lastReport != null) {
            last = lastReport.getSyncTime();
        }
        if (lastTransfer != null && (last == null || (lastTransfer.getSyncTime() != null
                && lastTransfer.getSyncTime().isAfter(last)))) {
            last = lastTransfer.getSyncTime();
        }
        vo.setLastSyncTime(last);
        return vo;
    }

    private void processReports() {
        var page = reportMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 50),
                new LambdaQueryWrapper<MesReport>()
                        .in(MesReport::getSyncStatus, MesConstants.SYNC_PENDING, MesConstants.SYNC_FAILED)
                        .and(w -> w.isNull(MesReport::getNextRetryTime).or().le(MesReport::getNextRetryTime, LocalDateTime.now()))
                        .orderByAsc(MesReport::getCreateTime));
        for (MesReport report : page.getRecords()) {
            pushReport(report);
        }
    }

    private void processTransfers() {
        var page = transferMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 50),
                new LambdaQueryWrapper<MesTransfer>()
                        .in(MesTransfer::getSyncStatus, MesConstants.SYNC_PENDING, MesConstants.SYNC_FAILED)
                        .and(w -> w.isNull(MesTransfer::getNextRetryTime).or().le(MesTransfer::getNextRetryTime, LocalDateTime.now()))
                        .orderByAsc(MesTransfer::getCreateTime));
        for (MesTransfer transfer : page.getRecords()) {
            pushTransfer(transfer);
        }
    }

    private void pushReport(MesReport report) {
        report.setSyncStatus(MesConstants.SYNC_SYNCING);
        reportMapper.updateById(report);
        try {
            String payload = MesReportSaveBuilder.build(objectMapper, kingdeeProperties, report);
            KingdeeSyncResult result = kingdeeCloudService.rawSave(
                    kingdeeProperties.getMesReportFormId(), payload, kingdeeProperties.isMesReportAutoAudit());
            applyReportResult(report, result);
        } catch (Exception e) {
            log.error("MES report sync failed reportNo={}", report.getReportNo(), e);
            applyReportFailure(report, e.getMessage());
        }
    }

    private void pushTransfer(MesTransfer transfer) {
        transfer.setSyncStatus(MesConstants.SYNC_SYNCING);
        transferMapper.updateById(transfer);
        try {
            String payload = MesTransferSaveBuilder.build(objectMapper, kingdeeProperties, transfer);
            KingdeeSyncResult result = kingdeeCloudService.rawSave(
                    kingdeeProperties.getMesTransferFormId(), payload, kingdeeProperties.isMesTransferAutoAudit());
            applyTransferResult(transfer, result);
        } catch (Exception e) {
            log.error("MES transfer sync failed transferNo={}", transfer.getTransferNo(), e);
            applyTransferFailure(transfer, e.getMessage());
        }
    }

    private void applyReportResult(MesReport report, KingdeeSyncResult result) {
        if (result != null && result.isSuccess()) {
            report.setSyncStatus(MesConstants.SYNC_SUCCESS);
            report.setErpBillNo(result.getBillNo());
            report.setSyncTime(LocalDateTime.now());
            report.setFailReason(null);
            reportMapper.updateById(report);
            trackSync(true, "REPORT", report.getReportNo(), null);
            return;
        }
        applyReportFailure(report, result == null ? "ERP无响应" : result.getMessage());
    }

    private void applyTransferResult(MesTransfer transfer, KingdeeSyncResult result) {
        if (result != null && result.isSuccess()) {
            transfer.setSyncStatus(MesConstants.SYNC_SUCCESS);
            transfer.setErpBillNo(result.getBillNo());
            transfer.setSyncTime(LocalDateTime.now());
            transfer.setFailReason(null);
            transferMapper.updateById(transfer);
            trackSync(true, "TRANSFER", transfer.getTransferNo(), null);
            return;
        }
        applyTransferFailure(transfer, result == null ? "ERP无响应" : result.getMessage());
    }

    private void applyReportFailure(MesReport report, String message) {
        int retry = report.getRetryCount() == null ? 0 : report.getRetryCount();
        retry++;
        report.setRetryCount(retry);
        report.setFailReason(trimReason(message));
        report.setSyncTime(LocalDateTime.now());
        if (!MesRetryBackoff.retryable(message)
                || MesRetryBackoff.exhausted(retry, mesProperties.getMaxRetry())) {
            report.setSyncStatus(MesConstants.SYNC_MANUAL);
            report.setNextRetryTime(null);
        } else {
            report.setSyncStatus(MesConstants.SYNC_FAILED);
            report.setNextRetryTime(MesRetryBackoff.nextRetryTime(retry - 1, LocalDateTime.now()));
        }
        reportMapper.updateById(report);
        trackSync(false, "REPORT", report.getReportNo(), report.getFailReason());
    }

    private void applyTransferFailure(MesTransfer transfer, String message) {
        int retry = transfer.getRetryCount() == null ? 0 : transfer.getRetryCount();
        retry++;
        transfer.setRetryCount(retry);
        transfer.setFailReason(trimReason(message));
        transfer.setSyncTime(LocalDateTime.now());
        if (!MesRetryBackoff.retryable(message)
                || MesRetryBackoff.exhausted(retry, mesProperties.getMaxRetry())) {
            transfer.setSyncStatus(MesConstants.SYNC_MANUAL);
            transfer.setNextRetryTime(null);
        } else {
            transfer.setSyncStatus(MesConstants.SYNC_FAILED);
            transfer.setNextRetryTime(MesRetryBackoff.nextRetryTime(retry - 1, LocalDateTime.now()));
        }
        transferMapper.updateById(transfer);
        trackSync(false, "TRANSFER", transfer.getTransferNo(), transfer.getFailReason());
    }

    private void markStale() {
        LocalDateTime pendingDeadline = LocalDateTime.now().minusDays(mesProperties.getPendingKeepDays());
        List<MesReport> stale = reportMapper.selectList(new LambdaQueryWrapper<MesReport>()
                .in(MesReport::getSyncStatus, MesConstants.SYNC_PENDING, MesConstants.SYNC_FAILED)
                .le(MesReport::getCreateTime, pendingDeadline));
        for (MesReport report : stale) {
            report.setSyncStatus(MesConstants.SYNC_STALE);
            report.setFailReason("长期未同步");
            reportMapper.updateById(report);
        }
    }

    public PageResult<MesErpOutbox> pageOutbox(String bizType, String bizNo, String syncStatus, long current, long size) {
        LambdaQueryWrapper<MesErpOutbox> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(bizType), MesErpOutbox::getBizType, bizType)
                .eq(StringUtils.hasText(bizNo), MesErpOutbox::getBizNo, bizNo)
                .eq(StringUtils.hasText(syncStatus), MesErpOutbox::getSyncStatus, syncStatus)
                .orderByDesc(MesErpOutbox::getCreateTime);
        var page = outboxMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public void retryOutbox(Long id) {
        MesErpOutbox task = outboxMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "同步任务不存在");
        }
        if (MesConstants.SYNC_SUCCESS.equals(task.getSyncStatus())
                || MesConstants.SYNC_CANCELLED.equals(task.getSyncStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可重试");
        }
        task.setSyncStatus(MesConstants.SYNC_PENDING);
        task.setNextRetryTime(LocalDateTime.now());
        task.setFailReason(null);
        outboxMapper.updateById(task);
    }

    private void processOutbox() {
        var page = outboxMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 50),
                new LambdaQueryWrapper<MesErpOutbox>()
                        .in(MesErpOutbox::getSyncStatus, MesConstants.SYNC_PENDING, MesConstants.SYNC_FAILED)
                        .and(w -> w.isNull(MesErpOutbox::getNextRetryTime)
                                .or().le(MesErpOutbox::getNextRetryTime, LocalDateTime.now()))
                        .orderByAsc(MesErpOutbox::getCreateTime));
        for (MesErpOutbox task : page.getRecords()) {
            pushOutbox(task);
        }
    }

    private void pushOutbox(MesErpOutbox task) {
        task.setSyncStatus(MesConstants.SYNC_SYNCING);
        outboxMapper.updateById(task);
        String formId = formIdOf(task.getBizType());
        if (!StringUtils.hasText(formId)) {
            markOutboxManual(task, "未配置金蝶单据 FormId，请配置后再同步");
            return;
        }
        try {
            KingdeeSyncResult result = MesConstants.ACTION_SUBMIT.equals(task.getAction())
                    ? submitOutboxBill(task, formId)
                    : createOutboxBill(task, formId);
            applyOutboxResult(task, result);
        } catch (Exception e) {
            log.error("MES outbox sync failed id={} type={} no={}", task.getId(), task.getBizType(), task.getBizNo(), e);
            applyOutboxFailure(task, e.getMessage());
        }
    }

    private KingdeeSyncResult createOutboxBill(MesErpOutbox task, String formId) {
        MesDefect defect = findDefect(task.getBizNo());
        String payload;
        if (MesConstants.BIZ_REWORK.equals(task.getBizType())) {
            List<MesReworkOp> ops = reworkOpMapper.selectList(new LambdaQueryWrapper<MesReworkOp>()
                    .eq(MesReworkOp::getDefectNo, task.getBizNo())
                    .orderByAsc(MesReworkOp::getSeqNo));
            payload = MesReworkOrderSaveBuilder.build(objectMapper, kingdeeProperties, defect, ops);
        } else {
            payload = MesDefectSaveBuilder.build(objectMapper, kingdeeProperties, defect);
        }
        return kingdeeCloudService.rawSave(formId, payload, autoAuditOf(task.getBizType()));
    }

    private KingdeeSyncResult submitOutboxBill(MesErpOutbox task, String formId) {
        MesErpOutbox create = outboxMapper.selectOne(new LambdaQueryWrapper<MesErpOutbox>()
                .eq(MesErpOutbox::getBizType, task.getBizType())
                .eq(MesErpOutbox::getBizNo, task.getBizNo())
                .eq(MesErpOutbox::getAction, MesConstants.ACTION_CREATE));
        if (create == null || !MesConstants.SYNC_SUCCESS.equals(create.getSyncStatus())
                || !StringUtils.hasText(create.getErpBillNo())) {
            throw new IllegalStateException("单据尚未在 ERP 新增成功，暂不能提交");
        }
        return kingdeeCloudService.submitAndAuditExistingBill(formId, create.getErpBillNo(), create.getErpBillId());
    }

    private void applyOutboxResult(MesErpOutbox task, KingdeeSyncResult result) {
        if (result != null && result.isSuccess()) {
            task.setSyncStatus(MesConstants.SYNC_SUCCESS);
            task.setErpBillNo(result.getBillNo());
            task.setLastSyncTime(LocalDateTime.now());
            task.setFailReason(null);
            outboxMapper.updateById(task);
            trackSync(true, task.getBizType(), task.getBizNo(), null);
            return;
        }
        applyOutboxFailure(task, result == null ? "ERP无响应" : result.getMessage());
    }

    private void applyOutboxFailure(MesErpOutbox task, String message) {
        int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
        retry++;
        task.setRetryCount(retry);
        task.setFailReason(trimReason(message));
        task.setLastSyncTime(LocalDateTime.now());
        if (!MesRetryBackoff.retryable(message) || MesRetryBackoff.exhausted(retry, mesProperties.getMaxRetry())) {
            task.setSyncStatus(MesConstants.SYNC_MANUAL);
            task.setNextRetryTime(null);
        } else {
            task.setSyncStatus(MesConstants.SYNC_FAILED);
            task.setNextRetryTime(MesRetryBackoff.nextRetryTime(retry - 1, LocalDateTime.now()));
        }
        outboxMapper.updateById(task);
        trackSync(false, task.getBizType(), task.getBizNo(), task.getFailReason());
    }

    private void markOutboxManual(MesErpOutbox task, String reason) {
        task.setSyncStatus(MesConstants.SYNC_MANUAL);
        task.setFailReason(reason);
        task.setNextRetryTime(null);
        task.setLastSyncTime(LocalDateTime.now());
        outboxMapper.updateById(task);
    }

    private MesDefect findDefect(String defectNo) {
        MesDefect defect = defectMapper.selectOne(new LambdaQueryWrapper<MesDefect>()
                .eq(MesDefect::getDefectNo, defectNo));
        if (defect == null) {
            throw new IllegalStateException("不良单不存在: " + defectNo);
        }
        return defect;
    }

    private String formIdOf(String bizType) {
        return MesConstants.BIZ_REWORK.equals(bizType)
                ? kingdeeProperties.getMesReworkFormId()
                : kingdeeProperties.getMesDefectFormId();
    }

    private boolean autoAuditOf(String bizType) {
        return MesConstants.BIZ_REWORK.equals(bizType)
                ? kingdeeProperties.isMesReworkAutoAudit()
                : kingdeeProperties.isMesDefectAutoAudit();
    }

    private long countOutbox(String bizType, String status) {
        Long count = outboxMapper.selectCount(new LambdaQueryWrapper<MesErpOutbox>()
                .eq(MesErpOutbox::getBizType, bizType)
                .eq(MesErpOutbox::getSyncStatus, status));
        return count == null ? 0 : count;
    }

    private long countReport(String status) {
        Long count = reportMapper.selectCount(new LambdaQueryWrapper<MesReport>().eq(MesReport::getSyncStatus, status));
        return count == null ? 0 : count;
    }

    private long countTransfer(String status) {
        Long count = transferMapper.selectCount(new LambdaQueryWrapper<MesTransfer>().eq(MesTransfer::getSyncStatus, status));
        return count == null ? 0 : count;
    }

    private MesReport latestSyncedReport() {
        var page = reportMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1),
                new LambdaQueryWrapper<MesReport>()
                        .eq(MesReport::getSyncStatus, MesConstants.SYNC_SUCCESS)
                        .orderByDesc(MesReport::getSyncTime));
        return page.getRecords().isEmpty() ? null : page.getRecords().get(0);
    }

    private MesTransfer latestSyncedTransfer() {
        var page = transferMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1),
                new LambdaQueryWrapper<MesTransfer>()
                        .eq(MesTransfer::getSyncStatus, MesConstants.SYNC_SUCCESS)
                        .orderByDesc(MesTransfer::getSyncTime));
        return page.getRecords().isEmpty() ? null : page.getRecords().get(0);
    }

    private static String trimReason(String message) {
        if (!StringUtils.hasText(message)) {
            return "同步失败";
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
