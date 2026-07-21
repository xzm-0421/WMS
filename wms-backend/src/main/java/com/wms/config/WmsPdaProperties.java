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
     * true：库存先落库返回 PENDING，后台同步；失败可 Web/PDA 重试。
     */
    private boolean asyncErpSync = true;

    /**
     * 扫码时若会话明细已存在，则跳过金蝶 View 全量刷新。
     */
    private boolean scanSkipKingdeeRefresh = true;

    /**
     * 打开详情时优先用本地会话明细（跳过 View），超过该秒数则回源金蝶。
     * 0 表示始终回源。
     */
    private int detailLocalPreferSeconds = 120;
}
