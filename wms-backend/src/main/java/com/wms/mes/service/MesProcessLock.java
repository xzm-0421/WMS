package com.wms.mes.service;

import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.mes.MesProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 工序+工单维度排队锁，等待超时提示「工序繁忙」。
 *
 * <p>优先使用 Redis 分布式锁（支持多实例）；Redis 未配置或运行中不可用时，回退进程内
 * {@link ReentrantLock}（与 {@code PdaShortCache} 一致的「可选 + 本地回退」策略）。
 * Redis 锁持有期间由看门狗按租期的 1/3 自动续约，避免长操作被提前释放。</p>
 *
 * <p>注意：只有「获取锁」阶段的 Redis 故障才回退本地；业务动作自身抛出的异常
 * （包括 {@link DataAccessException}）原样向上抛出，绝不重放。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MesProcessLock {

    private static final String KEY_PREFIX = "wms:mes:op-lock:";
    private static final String MODE_LOCAL = "LOCAL";

    /** 释放锁：仅当 value 与自己的 token 匹配时才删除，避免误删他人锁。 */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** 续约：仅当 value 匹配时才刷新过期时间（毫秒）。 */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private final MesProperties mesProperties;
    private final ConcurrentHashMap<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "mes-op-lock-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    public <T> T withLock(String moNo, String processCode, Supplier<T> action) {
        String key = key(moNo, processCode);
        if (useRedis()) {
            String token = null;
            try {
                // 仅获取阶段允许回退：Redis 不可用则退化为本地锁
                token = acquireRedisLock(key);
            } catch (DataAccessException ex) {
                log.warn("MES 工序锁 Redis 不可用，回退本地锁, key={}, err={}", key, ex.getMessage());
            }
            if (token != null) {
                ScheduledFuture<?> renewal = startWatchdog(key, token, currentLease());
                try {
                    // 动作自身的异常不在此捕获，避免误回退/重放
                    return action.get();
                } finally {
                    if (renewal != null) {
                        renewal.cancel(false);
                    }
                    release(key, token);
                }
            }
        }
        return withLocalLock(key, action);
    }

    private boolean useRedis() {
        return !MODE_LOCAL.equalsIgnoreCase(mesProperties.getLockMode()) && redisTemplate != null;
    }

    private String acquireRedisLock(String key) {
        String token = UUID.randomUUID().toString();
        Duration lease = currentLease();
        long waitMs = Math.max(0L, mesProperties.getProcessLockWaitSeconds()) * 1000L;
        long interval = Math.max(20L, mesProperties.getLockRetryIntervalMs());
        long deadline = System.currentTimeMillis() + waitMs;
        while (true) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, lease);
            if (Boolean.TRUE.equals(acquired)) {
                return token;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw processBusy();
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw processBusy();
            }
        }
    }

    private Duration currentLease() {
        return Duration.ofSeconds(Math.max(1L, mesProperties.getLockLeaseSeconds()));
    }

    private ScheduledFuture<?> startWatchdog(String key, String token, Duration lease) {
        long periodMs = Math.max(1000L, lease.toMillis() / 3);
        try {
            return watchdog.scheduleAtFixedRate(() -> {
                try {
                    redisTemplate.execute(RENEW_SCRIPT, Collections.singletonList(key), token,
                            String.valueOf(lease.toMillis()));
                } catch (Exception e) {
                    log.warn("MES 工序锁续约失败, key={}, err={}", key, e.getMessage());
                }
            }, periodMs, periodMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("MES 工序锁看门狗启动失败, key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void release(String key, String token) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), token);
        } catch (Exception e) {
            log.warn("MES 工序锁释放失败（将到期自动释放）, key={}, err={}", key, e.getMessage());
        }
    }

    private <T> T withLocalLock(String key, Supplier<T> action) {
        ReentrantLock lock = localLocks.computeIfAbsent(key, ignore -> new ReentrantLock());
        boolean acquired;
        try {
            acquired = lock.tryLock(mesProperties.getProcessLockWaitSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw processBusy();
        }
        if (!acquired) {
            throw processBusy();
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private BusinessException processBusy() {
        return new BusinessException(ErrorCode.CONFLICT, "工序繁忙，请稍后重试。", "PROCESS_BUSY");
    }

    private String key(String moNo, String processCode) {
        return KEY_PREFIX + (moNo == null ? "" : moNo.trim()) + "#" + (processCode == null ? "" : processCode.trim());
    }

    @PreDestroy
    public void shutdown() {
        watchdog.shutdownNow();
    }
}
