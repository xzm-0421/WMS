package com.wms.mes.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MesSyncPanelVo {
    private long queueTotal;
    private long pendingCount;
    private long syncingCount;
    private long failedCount;
    private long todaySyncedCount;
    private String networkStatus;
    private LocalDateTime lastSyncTime;
    private LocalDateTime lastCheckTime;
    private String lastError;
}
