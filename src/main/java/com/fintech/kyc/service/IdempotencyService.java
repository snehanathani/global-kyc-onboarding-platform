package com.fintech.kyc.service;

import com.fintech.kyc.domain.model.IdempotencyRecord;
import com.fintech.kyc.domain.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration L1_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyRecordRepository recordRepository;
    private final ConcurrentHashMap<String, String> inMemoryFallbackCache = new ConcurrentHashMap<>();

    public IdempotencyService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            IdempotencyRecordRepository recordRepository) {
        this.redisTemplate = redisTemplate;
        this.recordRepository = recordRepository;
    }

    /**
     * Dual-tier idempotency acquisition:
     * 1. Check L1 in-memory / Redis cache for sub-millisecond fast rejection of duplicates.
     * 2. Insert into L2 durable PostgreSQL table with unique constraint to guarantee durability across Redis restarts.
     */
    @Transactional
    public boolean acquireLockOrToken(String idempotencyKey) {
        String redisKey = "idempotency:" + idempotencyKey;

        // --- Tier 1: Fast L1 Cache Check ---
        if (redisTemplate != null) {
            try {
                Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", L1_TTL);
                if (Boolean.FALSE.equals(isNew)) {
                    log.debug("L1 Redis cache hit: duplicate request for key {}", idempotencyKey);
                    return false;
                }
            } catch (Exception e) {
                log.warn("Redis unavailable for L1 check. Falling back to local cache: {}", e.getMessage());
                if (inMemoryFallbackCache.putIfAbsent(redisKey, "PROCESSING") != null) {
                    return false;
                }
            }
        } else {
            if (inMemoryFallbackCache.putIfAbsent(redisKey, "PROCESSING") != null) {
                return false;
            }
        }

        // --- Tier 2: Durable L2 Relational Check in PostgreSQL ---
        try {
            if (recordRepository.existsByIdempotencyKey(idempotencyKey)) {
                log.debug("L2 PostgreSQL hit: idempotency key {} already exists in database.", idempotencyKey);
                return false;
            }
            recordRepository.save(new IdempotencyRecord(idempotencyKey, "PROCESSING"));
            return true;
        } catch (DataIntegrityViolationException dive) {
            log.warn("L2 unique constraint caught concurrent duplicate for key: {}", idempotencyKey);
            return false;
        }
    }

    @Transactional
    public void markCompleted(String idempotencyKey, String outcome) {
        String redisKey = "idempotency:" + idempotencyKey;

        // Update L1
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(redisKey, outcome, L1_TTL);
            } catch (Exception e) {
                log.warn("Failed to store completion state in Redis: {}", e.getMessage());
            }
        }
        inMemoryFallbackCache.put(redisKey, outcome);

        // Update L2 durable store
        Optional<IdempotencyRecord> recordOpt = recordRepository.findByIdempotencyKey(idempotencyKey);
        recordOpt.ifPresent(record -> {
            record.setStatus(outcome);
            record.setCompletedAt(Instant.now());
            recordRepository.save(record);
        });
    }
}
