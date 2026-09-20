package com.wms.mes.service;

import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.mes.MesProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 工序+工单维度排队锁，等待超时提示「工序繁忙」。
 */
@Component
@RequiredArgsConstructor
public class MesProcessLock {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final MesProperties mesProperties;

    public <T> T withLock(String moNo, String processCode, Supplier<T> action) {
        String key = (moNo == null ? "" : moNo.trim()) + "#" + (processCode == null ? "" : processCode.trim());
        ReentrantLock lock = locks.computeIfAbsent(key, ignore -> new ReentrantLock());
        boolean acquired;
        try {
            acquired = lock.tryLock(mesProperties.getProcessLockWaitSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONFLICT, "工序繁忙，请稍后重试。", "PROCESS_BUSY");
        }
        if (!acquired) {
            throw new BusinessException(ErrorCode.CONFLICT, "工序繁忙，请稍后重试。", "PROCESS_BUSY");
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
