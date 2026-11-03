package com.wise.kyc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, String> inMemoryFallbackCache = new ConcurrentHashMap<>();

    public IdempotencyService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquireLockOrToken(String idempotencyKey) {
        String redisKey = "idempotency:" + idempotencyKey;
        if (redisTemplate != null) {
            try {
                Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", DEFAULT_TTL);
                return Boolean.TRUE.equals(isNew);
            } catch (Exception e) {
                log.warn("Redis unavailable for idempotency check. Falling back to resilient in-memory lock: {}", e.getMessage());
            }
        }

        // Resilient in-memory fallback
        return inMemoryFallbackCache.putIfAbsent(redisKey, "PROCESSING") == null;
    }

    public void markCompleted(String idempotencyKey, String outcome) {
        String redisKey = "idempotency:" + idempotencyKey;
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(redisKey, outcome, DEFAULT_TTL);
                return;
            } catch (Exception e) {
                log.warn("Failed to store completion state in Redis: {}", e.getMessage());
            }
        }
        inMemoryFallbackCache.put(redisKey, outcome);
    }
}
