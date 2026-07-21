package com.wms.barcode.schedule;

import com.wms.barcode.service.BarcodeArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BarcodeArchiveCleanupScheduler {

    private final BarcodeArchiveService archiveService;

    /** 每天凌晨 3 点清理过期存档 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        try {
            int removed = archiveService.cleanupExpired();
            if (removed > 0) {
                log.info("定时任务：条码存档清理 {} 条", removed);
            }
        } catch (Exception ex) {
            log.warn("条码存档清理失败: {}", ex.getMessage());
        }
    }
}
