package com.fintech.kyc.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxMessageRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxMessageRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY) // Must participate in calling transaction
    public OutboxMessage scheduleEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxMessage message = new OutboxMessage(aggregateType, aggregateId, eventType, payloadJson);
            OutboxMessage saved = outboxRepository.save(message);
            log.info("Transactional Outbox message {} scheduled for aggregate {} ({})",
                    saved.getId(), aggregateId, eventType);
            return saved;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for aggregate {}", aggregateId, e);
            throw new RuntimeException("Serialization failure for outbox event", e);
        }
    }
}
