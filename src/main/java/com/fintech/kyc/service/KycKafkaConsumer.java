package com.fintech.kyc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.kyc.outbox.KycEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KycKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KycKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;

    public KycKafkaConsumer(ObjectMapper objectMapper, IdempotencyService idempotencyService) {
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
    }

    @KafkaListener(topics = "kyc.verification.completed", groupId = "kyc-downstream-account-activator", autoStartup = "${kafka.listener.auto-startup:false}")
    public void handleVerificationCompleted(String messagePayload) {
        try {
            KycEventPayload event = objectMapper.readValue(messagePayload, KycEventPayload.class);
            String consumerIdempotencyKey = "consumer:completed:" + event.caseId();

            if (!idempotencyService.acquireLockOrToken(consumerIdempotencyKey)) {
                log.info("Consumer idempotency: Event for caseId {} already processed. Skipping duplicate.", event.caseId());
                return;
            }

            log.info("[KAFKA CONSUMER - DOWNSTREAM ACTIVATION] Customer {} successfully verified in jurisdiction {}. Unlocking transfer limits and activating multi-currency account.",
                    event.customerId(), event.jurisdiction());

            idempotencyService.markCompleted(consumerIdempotencyKey, "ACTIVATED");
        } catch (Exception e) {
            log.error("Failed to process kyc.verification.completed event: {}", messagePayload, e);
        }
    }

    @KafkaListener(topics = "kyc.agent.review_required", groupId = "kyc-compliance-ops-notifier", autoStartup = "${kafka.listener.auto-startup:false}")
    public void handleReviewRequired(String messagePayload) {
        try {
            KycEventPayload event = objectMapper.readValue(messagePayload, KycEventPayload.class);
            log.warn("[KAFKA CONSUMER - OPS NOTIFIER] Manual review requested for caseId={}. Routing to high-priority triage queue. Rationale={}",
                    event.caseId(), event.rationale());
        } catch (Exception e) {
            log.error("Failed to process kyc.agent.review_required event: {}", messagePayload, e);
        }
    }
}
