package com.wms.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.integration.entity.ErpSyncLog;
import com.wms.integration.mapper.ErpSyncLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ErpSyncLogService {

    public static final String SOURCE_PDA_INBOUND = "PDA_INBOUND";
    public static final String SOURCE_PDA_OUTBOUND = "PDA_OUTBOUND";
    private static final int ERROR_MESSAGE_MAX_LEN = 500;

    private final ErpSyncLogMapper logMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void writeLog(String sourceType, String sourceNo, String request, String response,
                         boolean success, String errorMessage, int retryCount) {
        ErpSyncLog log = new ErpSyncLog();
        log.setSourceType(sourceType);
        log.setSourceNo(sourceNo);
        log.setRequestPayload(request);
        log.setResponsePayload(response);
        log.setStatus(success ? "SUCCESS" : "FAILED");
        log.setErrorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX_LEN));
        log.setRetryCount(retryCount);
        log.setCreateTime(LocalDateTime.now());
        logMapper.insert(log);
    }

    private static String truncate(String value, int maxLen) {
        if (!StringUtils.hasText(value) || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    /** 金蝶错误信息可能很长，写入 NVARCHAR(500) 字段前截断。 */
    public static String truncateMessage(String message) {
        return truncate(message, ERROR_MESSAGE_MAX_LEN);
    }

    public List<ErpSyncLog> listBySource(String sourceType, String sourceNo) {
        return logMapper.selectList(new LambdaQueryWrapper<ErpSyncLog>()
                .eq(ErpSyncLog::getSourceType, sourceType)
                .eq(ErpSyncLog::getSourceNo, sourceNo)
                .orderByDesc(ErpSyncLog::getCreateTime));
    }
}
