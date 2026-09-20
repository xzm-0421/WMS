package com.wms.mobile.service;

import com.wms.config.WmsPdaProperties;
import com.wms.mobile.dto.AppUpdateCheckVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MobileAppUpdateService {

    private final WmsPdaProperties pdaProperties;

    public AppUpdateCheckVo checkUpdate(Integer clientVersionCode) {
        WmsPdaProperties.AppUpdate cfg = pdaProperties.getAppUpdate();
        if (cfg == null) {
            cfg = new WmsPdaProperties.AppUpdate();
        }
        int clientCode = clientVersionCode != null ? clientVersionCode : 0;
        int latestCode = cfg.getVersionCode();
        boolean enabled = cfg.isEnabled();
        boolean hasUpdate = enabled && latestCode > clientCode && StringUtils.hasText(cfg.getDownloadUrl());

        String message;
        if (!enabled) {
            message = "更新检测未启用";
        } else if (!StringUtils.hasText(cfg.getDownloadUrl())) {
            message = "暂未配置更新包地址";
        } else if (hasUpdate) {
            message = "发现新版本 " + cfg.getVersionName();
        } else {
            message = "已是最新版本";
        }

        return AppUpdateCheckVo.builder()
                .hasUpdate(hasUpdate)
                .currentVersionCode(clientCode)
                .latestVersionName(cfg.getVersionName())
                .latestVersionCode(latestCode)
                .changelog(cfg.getChangelog() != null ? cfg.getChangelog() : "")
                .force(cfg.isForce())
                .packageType(StringUtils.hasText(cfg.getPackageType()) ? cfg.getPackageType().trim().toLowerCase() : "wgt")
                .downloadUrl(cfg.getDownloadUrl() != null ? cfg.getDownloadUrl().trim() : "")
                .message(message)
                .build();
    }
}
