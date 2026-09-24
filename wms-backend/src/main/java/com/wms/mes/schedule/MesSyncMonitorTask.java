package com.wms.mes.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.mes.MesConstants;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesTransfer;
import com.wms.mes.mapper.MesReportMapper;
import com.wms.mes.mapper.MesTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轻 MES 同步 SLA 监控：5 分钟内未回写 ERP 的报工/转移记录告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MesSyncMonitorTask {

    private static final long SLA_MINUTES = 5L;

    private final MesReportMapper reportMapper;
    private final MesTransferMapper transferMapper;

    /**
     * 每分钟检查一次 5 分钟 SLA。
     */
    @Scheduled(initialDelay = 60_000, fixedRate = 60_000)
    public void monitorSyncSla() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(SLA_MINUTES);

        List<MesReport> overdueReports = reportMapper.selectList(new LambdaQueryWrapper<MesReport>()
                .in(MesReport::getSyncStatus, MesConstants.SYNC_PENDING, MesConstants.SYNC_SYNCING)
                .lt(MesReport::getReportTime, deadline));
        for (MesReport report : overdueReports) {
            log.warn("MES SLA 超时: reportNo={}, moNo={}, reportTime={}",
                    report.getReportNo(), report.getMoNo(), report.getReportTime());
        }

        List<MesTransfer> overdueTransfers = transferMapper.selectList(new LambdaQueryWrapper<MesTransfer>()
                .in(MesTransfer::getSyncStatus, MesConstants.SYNC_PENDING, MesConstants.SYNC_SYNCING)
                .lt(MesTransfer::getTransferTime, deadline));
        for (MesTransfer transfer : overdueTransfers) {
            log.warn("MES SLA 超时: transferNo={}, moNo={}, transferTime={}",
                    transfer.getTransferNo(), transfer.getMoNo(), transfer.getTransferTime());
        }

        if (!overdueReports.isEmpty() || !overdueTransfers.isEmpty()) {
            log.warn("发现 {} 条报工、{} 条转移超过 {} 分钟 SLA 未同步",
                    overdueReports.size(), overdueTransfers.size(), SLA_MINUTES);
        }
    }
}
