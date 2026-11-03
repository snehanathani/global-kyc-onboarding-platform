package com.fintech.kyc.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxMessageRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxMessageRepository outboxRepository,
                           @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000) // Poll every 2 seconds for pending outbox events
    @Transactional
    public void publishPendingEvents() {
        List<OutboxMessage> pending = outboxRepository.findPendingMessagesToPublish();
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending Outbox messages to publish to Kafka", pending.size());

        for (OutboxMessage msg : pending) {
            String topic = resolveTopic(msg.getEventType());
            try {
                if (kafkaTemplate != null) {
                    kafkaTemplate.send(topic, msg.getAggregateId(), msg.getPayloadJson());
                } else {
                    log.info("[EMULATED KAFKA DISPATCH] Topic='{}', Key='{}', Payload={}",
                            topic, msg.getAggregateId(), msg.getPayloadJson());
                }
                msg.setStatus(OutboxMessage.OutboxStatus.PUBLISHED);
                msg.setProcessedAt(Instant.now());
                outboxRepository.save(msg);
                log.info("Outbox message {} published successfully to topic '{}'", msg.getId(), topic);
            } catch (Exception e) {
                msg.incrementRetry();
                msg.setLastError(e.getMessage());
                if (msg.getRetryCount() >= 5) {
                    msg.setStatus(OutboxMessage.OutboxStatus.FAILED);
                    log.error("Outbox message {} exceeded max retries. Marked as FAILED.", msg.getId(), e);
                } else {
                    log.warn("Failed to publish outbox message {}. Retrying later (attempt {})",
                            msg.getId(), msg.getRetryCount(), e);
                }
                outboxRepository.save(msg);
            }
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "KYC_VERIFICATION_COMPLETED" -> "kyc.verification.completed";
            case "KYC_MANUAL_REVIEW_REQUIRED" -> "kyc.agent.review_required";
            case "KYC_VERIFICATION_REJECTED" -> "kyc.verification.rejected";
            default -> "kyc.events.general";
        };
    }
}
