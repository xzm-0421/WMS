package com.wms.mes.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wms.mes.MesConstants;
import com.wms.mes.MesProperties;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesTransfer;
import com.wms.mes.mapper.MesReportMapper;
import com.wms.mes.mapper.MesTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 轻 MES 暂存数据生命周期管理：过期待同步/失败记录标记，已同步记录归档统计。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MesDataCleanupTask {

    private final MesReportMapper reportMapper;
    private final MesTransferMapper transferMapper;
    private final MesProperties mesProperties;

    /**
     * 每天凌晨 2 点清理过期数据。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredData() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pendingExpiry = now.minusDays(mesProperties.getPendingKeepDays());

        int staleReports = markStaleReports(pendingExpiry);
        int staleTransfers = markStaleTransfers(pendingExpiry);
        int expiredReports = markExpiredReports(pendingExpiry);
        int expiredTransfers = markExpiredTransfers(pendingExpiry);

        long archiveReports = countArchivableReports(now);
        long archiveTransfers = countArchivableTransfers(now);

        log.info("MES 数据清理完成: 标记过期报工{}条/转移{}条, 标记超期报工{}条/转移{}条, 可归档报工{}条/转移{}条",
                staleReports, staleTransfers, expiredReports, expiredTransfers, archiveReports, archiveTransfers);
    }

    private int markStaleReports(LocalDateTime deadline) {
        return reportMapper.update(null, new LambdaUpdateWrapper<MesReport>()
                .eq(MesReport::getSyncStatus, MesConstants.SYNC_PENDING)
                .lt(MesReport::getCreateTime, deadline)
                .set(MesReport::getSyncStatus, MesConstants.SYNC_STALE)
                .set(MesReport::getFailReason, "长期未同步,已标记为过期"));
    }

    private int markStaleTransfers(LocalDateTime deadline) {
        return transferMapper.update(null, new LambdaUpdateWrapper<MesTransfer>()
                .eq(MesTransfer::getSyncStatus, MesConstants.SYNC_PENDING)
                .lt(MesTransfer::getCreateTime, deadline)
                .set(MesTransfer::getSyncStatus, MesConstants.SYNC_STALE)
                .set(MesTransfer::getFailReason, "长期未同步,已标记为过期"));
    }

    private int markExpiredReports(LocalDateTime deadline) {
        return reportMapper.update(null, new LambdaUpdateWrapper<MesReport>()
                .in(MesReport::getSyncStatus, MesConstants.SYNC_FAILED, MesConstants.SYNC_MANUAL)
                .lt(MesReport::getCreateTime, deadline)
                .set(MesReport::getSyncStatus, MesConstants.SYNC_EXPIRED)
                .set(MesReport::getFailReason, "超期未处理"));
    }

    private int markExpiredTransfers(LocalDateTime deadline) {
        return transferMapper.update(null, new LambdaUpdateWrapper<MesTransfer>()
                .in(MesTransfer::getSyncStatus, MesConstants.SYNC_FAILED, MesConstants.SYNC_MANUAL)
                .lt(MesTransfer::getCreateTime, deadline)
                .set(MesTransfer::getSyncStatus, MesConstants.SYNC_EXPIRED)
                .set(MesTransfer::getFailReason, "超期未处理"));
    }

    private long countArchivableReports(LocalDateTime now) {
        Long count = reportMapper.selectCount(new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getSyncStatus, MesConstants.SYNC_SUCCESS)
                .lt(MesReport::getSyncTime, now.minusDays(mesProperties.getSuccessKeepDays())));
        return count == null ? 0 : count;
    }

    private long countArchivableTransfers(LocalDateTime now) {
        Long count = transferMapper.selectCount(new LambdaQueryWrapper<MesTransfer>()
                .eq(MesTransfer::getSyncStatus, MesConstants.SYNC_SUCCESS)
                .lt(MesTransfer::getSyncTime, now.minusDays(mesProperties.getSuccessKeepDays())));
        return count == null ? 0 : count;
    }
}
