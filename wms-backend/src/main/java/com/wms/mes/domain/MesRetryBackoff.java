package com.wms.mes.domain;

import java.time.LocalDateTime;

/**
 * 同步失败指数退避：1/2/4/8/16 分钟，最多 5 次。
 */
public final class MesRetryBackoff {

    private static final int[] MINUTES = {1, 2, 4, 8, 16};

    private MesRetryBackoff() {
    }

    public static LocalDateTime nextRetryTime(int retryCountAlready, LocalDateTime from) {
        int index = Math.min(Math.max(retryCountAlready, 0), MINUTES.length - 1);
        LocalDateTime base = from == null ? LocalDateTime.now() : from;
        return base.plusMinutes(MINUTES[index]);
    }

    public static boolean exhausted(int retryCount, int maxRetry) {
        return retryCount >= maxRetry;
    }

    /**
     * 参数/格式类错误不自动重试。
     */
    public static boolean retryable(String failReason) {
        if (failReason == null || failReason.isBlank()) {
            return true;
        }
        String text = failReason.trim();
        return !text.contains("参数") && !text.contains("格式") && !text.contains("字段映射");
    }
}
