package com.wms.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PDA 短缓存：优先 Redis，不可用时回落进程内 ConcurrentHashMap。
 */
@Slf4j
@Component
public class PdaShortCache {

    private static final String PREFIX = "wms:pda:";

    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> memory = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public PdaShortCache(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T get(String key, Class<T> type) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String full = PREFIX + key;
        try {
            if (redisTemplate != null) {
                String json = redisTemplate.opsForValue().get(full);
                if (StringUtils.hasText(json)) {
                    return objectMapper.readValue(json, type);
                }
                return null;
            }
        } catch (Exception e) {
            log.debug("Redis get miss/fail key={}: {}", full, e.getMessage());
        }
        CacheEntry entry = memory.get(full);
        if (entry == null || entry.expireAtMs < System.currentTimeMillis()) {
            if (entry != null) {
                memory.remove(full);
            }
            return null;
        }
        try {
            return objectMapper.convertValue(entry.value, type);
        } catch (Exception e) {
            memory.remove(full);
            return null;
        }
    }

    public <T> T get(String key, TypeReference<T> type) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String full = PREFIX + key;
        try {
            if (redisTemplate != null) {
                String json = redisTemplate.opsForValue().get(full);
                if (StringUtils.hasText(json)) {
                    return objectMapper.readValue(json, type);
                }
                return null;
            }
        } catch (Exception e) {
            log.debug("Redis get miss/fail key={}: {}", full, e.getMessage());
        }
        CacheEntry entry = memory.get(full);
        if (entry == null || entry.expireAtMs < System.currentTimeMillis()) {
            if (entry != null) {
                memory.remove(full);
            }
            return null;
        }
        try {
            return objectMapper.convertValue(entry.value, type);
        } catch (Exception e) {
            memory.remove(full);
            return null;
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (!StringUtils.hasText(key) || value == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        String full = PREFIX + key;
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(full, objectMapper.writeValueAsString(value), ttl);
                return;
            }
        } catch (Exception e) {
            log.debug("Redis put fail key={}: {}", full, e.getMessage());
        }
        memory.put(full, new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis()));
        if (memory.size() > 2000) {
            long now = System.currentTimeMillis();
            memory.entrySet().removeIf(e -> e.getValue().expireAtMs < now);
        }
    }

    public void evict(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        String full = PREFIX + key;
        memory.remove(full);
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(full);
            }
        } catch (Exception ignored) {
        }
    }

    public void evictByPrefix(String keyPrefix) {
        if (!StringUtils.hasText(keyPrefix)) {
            return;
        }
        String fullPrefix = PREFIX + keyPrefix;
        memory.keySet().removeIf(k -> k.startsWith(fullPrefix));
        // Redis 前缀删除成本高；短 TTL 自然过期即可。业务侧精确 evict 具体 key。
    }

    private record CacheEntry(Object value, long expireAtMs) {
    }
}
