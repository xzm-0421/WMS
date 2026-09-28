package com.wms.mobile.dto;

import lombok.Data;

/**
 * 移动端轻MES首页统计（按当前登录人）。
 */
@Data
public class MobileMesSummaryVo {

    /** 本人今日报工数 */
    private long todayReportCount;

    /** 本人待同步（报工 + 转移） */
    private long pendingCount;

    /** 本人同步失败（报工 + 转移） */
    private long failedCount;

    /** 未完成工序计划数（计划数量 > 已报工数量） */
    private long incompletePlanCount;

    /** 未处理不良单数（返工中 / 二次返工中） */
    private long openDefectCount;

    /** ERP 网络状态 ONLINE / OFFLINE */
    private String networkStatus;
}
