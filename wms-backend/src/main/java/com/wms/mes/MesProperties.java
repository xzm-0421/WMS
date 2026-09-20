package com.wms.mes;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 轻 MES 运行参数（轮询、超收默认值、队列容量等）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mes")
public class MesProperties {

    /** 定时从 ERP 拉取基础资料 */
    private boolean masterDataPollEnabled = true;

    /** 基础资料轮询间隔，默认 30 分钟 */
    private long masterDataPollMs = 1_800_000L;

    /** 定时从 ERP 拉取工序计划 */
    private boolean planPollEnabled = true;

    /** 工序计划轮询间隔，默认 1 分钟 */
    private long planPollMs = 60_000L;

    /** 报工/转移异步回写 Worker 间隔 */
    private long syncWorkerMs = 10_000L;

    /** ERP 健康检查间隔 */
    private long healthCheckMs = 10_000L;

    /** 连续失败几次判定离线 */
    private int healthFailThreshold = 3;

    /** 同步最大重试次数 */
    private int maxRetry = 5;

    /** 待同步队列容量上限 */
    private int queueCapacity = 10_000;

    /** 待同步/失败记录保留天数 */
    private int pendingKeepDays = 30;

    /** 已同步记录保留天数 */
    private int successKeepDays = 90;

    /** 已取消记录保留天数 */
    private int cancelledKeepDays = 180;

    /** 默认超收比例（ERP 未配置时） */
    private BigDecimal defaultOverReceiveRatio = BigDecimal.ZERO;

    /** 工序锁等待秒数 */
    private long processLockWaitSeconds = 30L;
}
