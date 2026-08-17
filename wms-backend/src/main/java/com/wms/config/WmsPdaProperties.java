package com.wms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PDA / 通知单扫码性能相关开关。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wms.pda")
public class WmsPdaProperties {

    /** 列表每次向金蝶拉取的最大行数（再在内存分页） */
    private int listFetchLimit = 200;

    /** 短缓存 TTL 秒（列表/详情摘要），0 关闭 */
    private int shortCacheTtlSeconds = 60;

    /**
     * 提交时是否异步同步金蝶（Save/Submit/Audit）。
     * false（默认）：提交接口内同步完成，PDA 可立即提示成败并失败不退出。
     * true：库存先落库返回 PENDING，后台同步；PDA 无法当场确认金蝶结果，失败需 Web 查批次。
     */
    private boolean asyncErpSync = false;

    /**
     * 扫码时若会话明细已存在，则跳过金蝶 View 全量刷新。
     */
    private boolean scanSkipKingdeeRefresh = true;

    /**
     * 打开详情时优先用本地会话明细（跳过 View），超过该秒数则回源金蝶。
     * 0 表示始终回源。
     */
    private int detailLocalPreferSeconds = 120;

    /** PDA 应用热更新 / 整包更新配置 */
    private AppUpdate appUpdate = new AppUpdate();

    @Data
    public static class AppUpdate {
        /** 服务端最新 versionName，如 1.0.1 */
        private String versionName = "1.0.0";
        /** 服务端最新 versionCode，须大于客户端才提示更新 */
        private int versionCode = 100;
        /** 更新说明 */
        private String changelog = "";
        /** 是否强制更新 */
        private boolean force = false;
        /**
         * 安装包类型：wgt（热更新）/ apk（整包）
         */
        private String packageType = "wgt";
        /**
         * 下载地址：绝对 URL，或相对站点根路径如 /pda-update/wms-pda.wgt
         */
        private String downloadUrl = "";
        /** 是否启用更新检测（关闭时接口仍返回当前配置，但 hasUpdate=false） */
        private boolean enabled = true;
    }
}
