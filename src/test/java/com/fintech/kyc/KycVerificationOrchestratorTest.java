package com.fintech.kyc;

import com.fintech.kyc.domain.model.CustomerType;
import com.fintech.kyc.domain.model.Jurisdiction;
import com.fintech.kyc.domain.model.VerificationStatus;
import com.fintech.kyc.domain.repository.IdempotencyRecordRepository;
import com.fintech.kyc.dto.InitiateVerificationRequest;
import com.fintech.kyc.dto.VerificationCaseResponse;
import com.fintech.kyc.outbox.OutboxMessage;
import com.fintech.kyc.outbox.OutboxMessageRepository;
import com.fintech.kyc.resilience.PrimaryIdentityVendor;
import com.fintech.kyc.resilience.SanctionsVendorClient;
import com.fintech.kyc.resilience.SecondaryIdentityVendor;
import com.fintech.kyc.service.KycVerificationOrchestrator;
import com.fintech.kyc.service.exception.IdempotencyPayloadMismatchException;
import org.junit.jupiter.api.AfterEach;
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

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private PrimaryIdentityVendor primaryVendor;

    @Autowired
    private SecondaryIdentityVendor secondaryVendor;

    @Autowired
    private SanctionsVendorClient sanctionsClient;

    @AfterEach
    void tearDown() {
        primaryVendor.setSimulateOutage(false);
        secondaryVendor.setSimulateOutage(false);
        sanctionsClient.setSimulateOutage(false);
    }

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
        assertEquals(2, response.checks().size());

        // Verify Transactional Outbox event created
        List<OutboxMessage> outbox = outboxRepository.findByStatus(OutboxMessage.OutboxStatus.PENDING);
        boolean eventFound = outbox.stream()
                .anyMatch(o -> o.getAggregateId().equals(response.customerId()) &&
                               o.getEventType().equals("KYC_VERIFICATION_COMPLETED"));
        assertTrue(eventFound, "Transactional Outbox event KYC_VERIFICATION_COMPLETED must be recorded");

        // Verify durable idempotency record in PostgreSQL
        assertTrue(idempotencyRecordRepository.existsByIdempotencyKey("idem-key-stp-001"),
                "Durable idempotency record must exist in PostgreSQL");
    }

    @Test
    @DisplayName("Invariant: Double vendor outage withholds auto-approval (Case degrades to PENDING, never approved)")
    void testDoubleVendorOutageWithholdsApproval() {
        primaryVendor.setSimulateOutage(true);
        secondaryVendor.setSimulateOutage(true);

        InitiateVerificationRequest request = new InitiateVerificationRequest(
                "cust-blackout-002",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "Charles Darwin",
                null,
                new BigDecimal("2000.00"),
                "PASSPORT",
                "PASS-DOC-991",
                "idem-key-blackout-002"
        );

        VerificationCaseResponse response = orchestrator.initiateAndProcessVerification(request);

        assertNotNull(response);
        // CRITICAL INVARIANT: Must NOT be AUTO_APPROVED when identity checks could not be completed!
        assertEquals(VerificationStatus.PENDING, response.status());
        assertFalse(response.straightThroughProcessed(), "Cannot auto-approve when identity checks are pending");
        assertNotNull(response.rejectionReason());
        assertTrue(response.rejectionReason().contains("External verification providers temporarily unavailable"));
    }

    @Test
    @DisplayName("Invariant: Sanctions provider 5xx/Timeout strictly fails closed (Routes to Manual Review)")
    void testSanctionsVendorFailureFailsClosed() {
        sanctionsClient.setSimulateOutage(true);

        InitiateVerificationRequest request = new InitiateVerificationRequest(
                "cust-sanctions-down-003",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "Ada Lovelace",
                null,
                new BigDecimal("1800.00"),
                "PASSPORT",
                "PASS-ADA-101",
                "idem-key-sanctions-down-003"
        );

        VerificationCaseResponse response = orchestrator.initiateAndProcessVerification(request);

        assertNotNull(response);
        // STRICT INVARIANT: Sanctions vendor failure must withhold approval
        assertEquals(VerificationStatus.MANUAL_REVIEW_REQUIRED, response.status());
        assertFalse(response.straightThroughProcessed());
        assertTrue(response.rejectionReason().contains("Sanctions"));
    }

    @Test
    @DisplayName("Idempotency: Reusing the same key with different payload throws 422 Unprocessable Entity")
    void testIdempotencyPayloadMismatchThrows422() {
        InitiateVerificationRequest requestA = new InitiateVerificationRequest(
                "cust-payload-orig",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "Original User",
                null,
                new BigDecimal("1000.00"),
                "PASSPORT",
                "PASS-A",
                "idem-key-reused-shared"
        );

        orchestrator.initiateAndProcessVerification(requestA);

        // Submit completely different customer under the same idempotency key
        InitiateVerificationRequest requestB = new InitiateVerificationRequest(
                "cust-payload-different",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "Different User",
                null,
                new BigDecimal("50000.00"),
                "PASSPORT",
                "PASS-B",
                "idem-key-reused-shared"
        );

        assertThrows(IdempotencyPayloadMismatchException.class, () ->
                orchestrator.initiateAndProcessVerification(requestB)
        );
    }

    @Test
    @DisplayName("Customer with Sanctions hit is escalated to compliance review queue")
    void testSanctionsHitEscalation() {
        InitiateVerificationRequest request = new InitiateVerificationRequest(
                "cust-sanctions-005",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "SANCTIONED INDIVIDUAL TEST",
                null,
                new BigDecimal("2000.00"),
                "PASSPORT",
                "PASS-BAD-001",
                "idem-key-sanctions-005"
        );

        VerificationCaseResponse response = orchestrator.initiateAndProcessVerification(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.MANUAL_REVIEW_REQUIRED, response.status());
        assertFalse(response.straightThroughProcessed());
        assertNotNull(response.rejectionReason());
        assertTrue(response.rejectionReason().contains("Sanctions"));
    }

    @Test
    @DisplayName("High-volume customer (> GBP 50k) is routed to manual review")
    void testHighVolumeEscalation() {
        InitiateVerificationRequest request = new InitiateVerificationRequest(
                "cust-wealth-006",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "Wealthy Investor",
                null,
                new BigDecimal("150000.00"),
                "PASSPORT",
                "PASS-VIP-999",
                "idem-key-wealth-006"
        );

        VerificationCaseResponse response = orchestrator.initiateAndProcessVerification(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.MANUAL_REVIEW_REQUIRED, response.status());
        assertFalse(response.straightThroughProcessed());
    }

    @Test
    @DisplayName("Idempotent requests replay exact stored response without duplicate side-effects")
    void testIdempotencyReplayReturnsCachedResponse() {
        InitiateVerificationRequest request = new InitiateVerificationRequest(
                "cust-idem-007",
                CustomerType.INDIVIDUAL,
                Jurisdiction.EU,
                "Greta Garbo",
                null,
                new BigDecimal("3000.00"),
                "PASSPORT",
                "PASS-EU-3344",
                "idem-token-unique-007"
        );

        VerificationCaseResponse first = orchestrator.initiateAndProcessVerification(request);
        VerificationCaseResponse second = orchestrator.initiateAndProcessVerification(request);

        assertEquals(first.id(), second.id());
        assertEquals(first.status(), second.status());
    }
}
