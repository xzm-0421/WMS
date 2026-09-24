package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.integration.kingdee.KingdeeSyncResult;
import com.wms.mes.MesConstants;
import com.wms.mes.MesProperties;
import com.wms.mes.domain.MesRetryBackoff;
import com.wms.mes.dto.MesSyncPanelVo;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesRuntime;
import com.wms.mes.entity.MesTransfer;
import com.wms.mes.kingdee.MesReportSaveBuilder;
import com.wms.mes.kingdee.MesTransferSaveBuilder;
import com.wms.mes.mapper.MesReportMapper;
import com.wms.mes.mapper.MesTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报工/转移异步回写 ERP：指数退避、失败人工介入、同步面板统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesSyncWorkerService {

    private final MesReportMapper reportMapper;
    private final MesTransferMapper transferMapper;
    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties kingdeeProperties;
    private final MesProperties mesProperties;
    private final MesHealthService healthService;
    private final ObjectMapper objectMapper;

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
        markStale();
    }

    private void sendAlert(String title, String content) {
        // TODO: 集成钉钉/邮件/短信告警通道
        log.warn("告警: {} - {}", title, content);
    }

    public MesSyncPanelVo panel() {
        MesRuntime runtime = healthService.current();
        MesSyncPanelVo vo = new MesSyncPanelVo();
        vo.setNetworkStatus(runtime.getNetworkStatus());
        vo.setLastCheckTime(runtime.getLastCheckTime());
        vo.setLastError(runtime.getLastError());
        vo.setPendingCount(countReport(MesConstants.SYNC_PENDING) + countTransfer(MesConstants.SYNC_PENDING));
        vo.setSyncingCount(countReport(MesConstants.SYNC_SYNCING) + countTransfer(MesConstants.SYNC_SYNCING));
        vo.setFailedCount(countReport(MesConstants.SYNC_FAILED) + countReport(MesConstants.SYNC_MANUAL)
                + countTransfer(MesConstants.SYNC_FAILED) + countTransfer(MesConstants.SYNC_MANUAL));
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
