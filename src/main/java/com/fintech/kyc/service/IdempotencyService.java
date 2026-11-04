package com.fintech.kyc.service;

import com.fintech.kyc.domain.model.IdempotencyRecord;
import com.fintech.kyc.domain.repository.IdempotencyRecordRepository;
import com.fintech.kyc.service.exception.IdempotencyConflictException;
import com.fintech.kyc.service.exception.IdempotencyPayloadMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository recordRepository;

    public IdempotencyService(IdempotencyRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public record IdempotencyResult(
            boolean shouldProceed,
            String completedResponsePayload
    ) {
        public static IdempotencyResult proceed() {
            return new IdempotencyResult(true, null);
        }

        public static IdempotencyResult replay(String payload) {
            return new IdempotencyResult(false, payload);
        }
    }

    /**
     * Executes in an isolated, immediate transaction.
     * Guarantees that concurrent requests for the same idempotency key block or fail with HTTP 409 Conflict,
     * while different payloads under the same key fail with HTTP 422 Unprocessable Entity.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult acquireKey(String idempotencyKey, String requestHash) {
        Optional<IdempotencyRecord> existingOpt = recordRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            // Guard: Payload mismatch
            if (!existing.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency key {} reused with different request payload hash.", idempotencyKey);
                throw new IdempotencyPayloadMismatchException(
                        "Idempotency key '" + idempotencyKey + "' was previously used with a different request payload.");
            }

            // Guard: In flight concurrent request
            if (existing.getStatus() == IdempotencyRecord.Status.IN_PROGRESS) {
                log.warn("Concurrent in-flight duplicate detected for idempotency key {}", idempotencyKey);
                throw new IdempotencyConflictException(
                        "A request with idempotency key '" + idempotencyKey + "' is currently in progress. Please retry shortly.");
            }

            // Completed: Safe replay of stored response
            if (existing.getStatus() == IdempotencyRecord.Status.COMPLETED) {
                log.info("Idempotent replay: returning stored response for key {}", idempotencyKey);
                return IdempotencyResult.replay(existing.getResponsePayload());
            }
        }

        // Claim key atomically
        try {
            IdempotencyRecord newRecord = new IdempotencyRecord(idempotencyKey, requestHash);
            recordRepository.saveAndFlush(newRecord);
            return IdempotencyResult.proceed();
        } catch (DataIntegrityViolationException dive) {
            log.warn("Concurrent unique constraint race for key: {}", idempotencyKey);
            throw new IdempotencyConflictException(
                    "A request with idempotency key '" + idempotencyKey + "' was claimed concurrently.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String idempotencyKey, String serializedResponse) {
        recordRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyRecord.Status.COMPLETED);
            record.setResponsePayload(serializedResponse);
            record.setCompletedAt(Instant.now());
            recordRepository.save(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String idempotencyKey) {
        recordRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyRecord.Status.FAILED);
            recordRepository.save(record);
        });
    }
}
