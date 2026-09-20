package com.wms.mes.service;

import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.mes.MesConstants;
import com.wms.mes.MesProperties;
import com.wms.mes.entity.MesRuntime;
import com.wms.mes.mapper.MesRuntimeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ERP 连通性探测：连续失败进入离线，恢复后自动回到在线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesHealthService {

    private static final int RUNTIME_ID = 1;

    private final KingdeeCloudService kingdeeCloudService;
    private final MesRuntimeMapper runtimeMapper;
    private final MesProperties mesProperties;

    public MesRuntime current() {
        MesRuntime runtime = runtimeMapper.selectById(RUNTIME_ID);
        if (runtime == null) {
            runtime = new MesRuntime();
            runtime.setId(RUNTIME_ID);
            runtime.setNetworkStatus(MesConstants.NETWORK_OFFLINE);
            runtime.setFailStreak(0);
            runtimeMapper.insert(runtime);
        }
        return runtime;
    }

    public boolean isOnline() {
        MesRuntime runtime = current();
        return MesConstants.NETWORK_ONLINE.equals(runtime.getNetworkStatus());
    }

    public void check() {
        MesRuntime runtime = current();
        runtime.setLastCheckTime(LocalDateTime.now());
        boolean ok = kingdeeCloudService.ping();
        if (ok) {
            boolean wasOffline = !MesConstants.NETWORK_ONLINE.equals(runtime.getNetworkStatus());
            runtime.setFailStreak(0);
            runtime.setNetworkStatus(MesConstants.NETWORK_ONLINE);
            runtime.setLastOnlineTime(LocalDateTime.now());
            runtime.setLastError(null);
            runtimeMapper.updateById(runtime);
            if (wasOffline) {
                log.info("MES ERP network recovered");
            }
            return;
        }
        int streak = runtime.getFailStreak() == null ? 0 : runtime.getFailStreak();
        streak++;
        runtime.setFailStreak(streak);
        if (streak >= mesProperties.getHealthFailThreshold()) {
            runtime.setNetworkStatus(MesConstants.NETWORK_OFFLINE);
        }
        runtime.setLastError(kingdeeCloudService.isEnabled() ? "ERP健康检查失败" : "金蝶对接未启用");
        runtimeMapper.updateById(runtime);
        log.warn("MES ERP health check failed streak={}", streak);
    }
}
