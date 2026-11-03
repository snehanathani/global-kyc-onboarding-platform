package com.wise.kyc;

import com.wise.kyc.domain.model.CustomerType;
import com.wise.kyc.domain.model.Jurisdiction;
import com.wise.kyc.domain.model.VerificationStatus;
import com.wise.kyc.dto.InitiateVerificationRequest;
import com.wise.kyc.dto.VerificationCaseResponse;
import com.wise.kyc.outbox.OutboxMessage;
import com.wise.kyc.outbox.OutboxMessageRepository;
import com.wise.kyc.service.KycVerificationOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KycVerificationOrchestratorTest {

    @Autowired
    private KycVerificationOrchestrator orchestrator;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Test
    @DisplayName("Straight-Through Processing (STP) auto-approves compliant customer and schedules Outbox event")
    void testStraightThroughProcessingAutoApproval() {
        InitiateVerificationRequest request = new InitiateVerificationRequest(
                "cust-stp-001",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "Arthur Conan Doyle",
                null,
                new BigDecimal("1500.00"),
                "PASSPORT",
                "PASS-UK-888999",
                "idem-key-stp-001"
        );

        VerificationCaseResponse response = orchestrator.initiateAndProcessVerification(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.AUTO_APPROVED, response.status());
        assertTrue(response.straightThroughProcessed());
        assertEquals(2, response.checks().size()); // Identity + Sanctions

        // Verify Transactional Outbox event created
        List<OutboxMessage> outbox = outboxRepository.findByStatus(OutboxMessage.OutboxStatus.PENDING);
        boolean eventFound = outbox.stream()
                .anyMatch(o -> o.getAggregateId().equals(response.id()) &&
                               o.getEventType().equals("KYC_VERIFICATION_COMPLETED"));
        assertTrue(eventFound, "Transactional Outbox event KYC_VERIFICATION_COMPLETED must be recorded");
    }

    @Test
    @DisplayName("Customer with Sanctions hit is escalated to compliance review queue")
    void testSanctionsHitEscalation() {
        InitiateVerificationRequest request = new InitiateVerificationRequest(
                "cust-sanctions-002",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "SANCTIONED INDIVIDUAL TEST",
                null,
                new BigDecimal("2000.00"),
                "PASSPORT",
                "PASS-BAD-001",
                "idem-key-sanctions-002"
        );

        VerificationCaseResponse response = orchestrator.initiateAndProcessVerification(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.MANUAL_REVIEW_REQUIRED, response.status());
        assertFalse(response.straightThroughProcessed());
        assertNotNull(response.rejectionReason());
        assertTrue(response.rejectionReason().contains("Sanctions"));
    }

    @Test
    @DisplayName("High-volume customer (> £50k) is routed to manual review")
    void testHighVolumeEscalation() {
        InitiateVerificationRequest request = new InitiateVerificationRequest(
                "cust-wealth-003",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "Wealthy Investor",
                null,
                new BigDecimal("150000.00"),
                "PASSPORT",
                "PASS-VIP-999",
                "idem-key-wealth-003"
        );

        VerificationCaseResponse response = orchestrator.initiateAndProcessVerification(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.MANUAL_REVIEW_REQUIRED, response.status());
        assertFalse(response.straightThroughProcessed());
    }

    @Test
    @DisplayName("Idempotent requests return existing verification case without duplicate side-effects")
    void testIdempotencyProtection() {
        InitiateVerificationRequest request = new InitiateVerificationRequest(
                "cust-idem-004",
                CustomerType.INDIVIDUAL,
                Jurisdiction.EU,
                "Greta Garbo",
                null,
                new BigDecimal("3000.00"),
                "PASSPORT",
                "PASS-EU-3344",
                "idem-token-unique-004"
        );

        VerificationCaseResponse first = orchestrator.initiateAndProcessVerification(request);
        VerificationCaseResponse second = orchestrator.initiateAndProcessVerification(request);

        assertEquals(first.id(), second.id());
        assertEquals(first.status(), second.status());
    }
}
