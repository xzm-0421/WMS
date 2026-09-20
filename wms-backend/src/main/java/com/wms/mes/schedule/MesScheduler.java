package com.wms.mes.schedule;

import com.wms.mes.MesProperties;
import com.wms.mes.service.MesHealthService;
import com.wms.mes.service.MesKingdeePullService;
import com.wms.mes.service.MesSyncWorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轻 MES 定时任务：基础资料、工序计划、健康检查、异步回写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MesScheduler {

    private final MesProperties mesProperties;
    private final MesKingdeePullService pullService;
    private final MesHealthService healthService;
    private final MesSyncWorkerService syncWorkerService;

    private final AtomicBoolean pullingMaster = new AtomicBoolean(false);
    private final AtomicBoolean pullingPlan = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);

    @Scheduled(initialDelay = 45_000, fixedDelayString = "${mes.master-data-poll-ms:1800000}")
    public void pollMasterData() {
        if (!mesProperties.isMasterDataPollEnabled()) {
            return;
        }
        if (!pullingMaster.compareAndSet(false, true)) {
            return;
        }
        try {
            pullService.syncMasterData();
        } catch (Exception e) {
            log.error("MES master data poll failed", e);
        } finally {
            pullingMaster.set(false);
        }
    }

    @Scheduled(initialDelay = 20_000, fixedDelayString = "${mes.plan-poll-ms:60000}")
    public void pollOpPlans() {
        if (!mesProperties.isPlanPollEnabled()) {
            return;
        }
        if (!pullingPlan.compareAndSet(false, true)) {
            return;
        }
        try {
            pullService.syncOpPlans(null);
        } catch (Exception e) {
            log.error("MES op plan poll failed", e);
        } finally {
            pullingPlan.set(false);
        }
    }

    @Scheduled(initialDelay = 10_000, fixedDelayString = "${mes.health-check-ms:10000}")
    public void healthCheck() {
        try {
            healthService.check();
        } catch (Exception e) {
            log.error("MES health check failed", e);
        }
    }

    @Scheduled(initialDelay = 15_000, fixedDelayString = "${mes.sync-worker-ms:10000}")
    public void drainSyncQueue() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        try {
            syncWorkerService.drainQueue();
        } catch (Exception e) {
            log.error("MES sync worker failed", e);
        } finally {
            draining.set(false);
        }
    }
}
