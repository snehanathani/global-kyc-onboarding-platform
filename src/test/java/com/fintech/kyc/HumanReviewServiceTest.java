package com.fintech.kyc;

import com.fintech.kyc.domain.model.*;
import com.fintech.kyc.domain.repository.VerificationCaseRepository;
import com.fintech.kyc.dto.ManualReviewDecisionRequest;
import com.fintech.kyc.dto.VerificationCaseResponse;
import com.fintech.kyc.service.HumanReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HumanReviewServiceTest {

    @Autowired
    private HumanReviewService reviewService;

    @Autowired
    private VerificationCaseRepository caseRepository;

    @Test
    @DisplayName("Guard: Agent claim collision prevents second officer from overwriting claim")
    void testAgentClaimCollisionProtection() {
        VerificationCase kycCase = new VerificationCase(
                "cust-review-guard-1", CustomerType.INDIVIDUAL, Jurisdiction.GB,
                "Test User", null, new BigDecimal("5000")
        );
        kycCase.setStatus(VerificationStatus.MANUAL_REVIEW_REQUIRED);
        VerificationCase savedCase = caseRepository.save(kycCase);
        final String caseId = savedCase.getId();

        // Officer A claims
        reviewService.claimCase(caseId, "agent_alice");

        // Officer B tries to claim -> must throw IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reviewService.claimCase(caseId, "agent_bob")
        );
        assertTrue(ex.getMessage().contains("already claimed by"));
    }

    @Test
    @DisplayName("Guard: Approving case sets MANUALLY_APPROVED status (protecting STP metrics)")
    void testManualApprovalPreservesStpMetrics() {
        VerificationCase kycCase = new VerificationCase(
                "cust-review-guard-2", CustomerType.INDIVIDUAL, Jurisdiction.GB,
                "Test User 2", null, new BigDecimal("60000")
        );
        kycCase.setStatus(VerificationStatus.MANUAL_REVIEW_REQUIRED);
        VerificationCase savedCase = caseRepository.save(kycCase);
        final String caseId = savedCase.getId();

        reviewService.claimCase(caseId, "agent_alice");

        ManualReviewDecisionRequest decision = new ManualReviewDecisionRequest(
                "agent_alice", "APPROVE", "Proof of wealth documents verified from bank."
        );
        VerificationCaseResponse response = reviewService.submitReviewDecision(caseId, decision);

        // Invariant: Status must be MANUALLY_APPROVED, not AUTO_APPROVED
        assertEquals(VerificationStatus.MANUALLY_APPROVED, response.status());
        assertFalse(response.straightThroughProcessed());
    }

    @Test
    @DisplayName("Guard: Sanctions hit override requires explicit FOUR_EYES_VERIFIED sign-off")
    void testSanctionsOverrideRequiresFourEyes() {
        VerificationCase kycCase = new VerificationCase(
                "cust-review-guard-3", CustomerType.INDIVIDUAL, Jurisdiction.GB,
                "Sanctions Subject", null, new BigDecimal("5000")
        );
        kycCase.setStatus(VerificationStatus.MANUAL_REVIEW_REQUIRED);
        VerificationCheck sanctionsCheck = new VerificationCheck(CheckType.SANCTIONS_AND_PEP, "SANCTIONS_VENDOR");
        sanctionsCheck.setStatus(CheckStatus.FLAGGED);
        kycCase.addCheck(sanctionsCheck);
        VerificationCase savedCase = caseRepository.save(kycCase);
        final String caseId = savedCase.getId();

        reviewService.claimCase(caseId, "agent_alice");

        // Normal approval without four-eyes sign-off must fail
        ManualReviewDecisionRequest invalidDecision = new ManualReviewDecisionRequest(
                "agent_alice", "APPROVE", "I think this is fine."
        );
        assertThrows(IllegalStateException.class, () ->
                reviewService.submitReviewDecision(caseId, invalidDecision)
        );

        // Approval with four-eyes sign-off succeeds
        ManualReviewDecisionRequest validDecision = new ManualReviewDecisionRequest(
                "agent_alice", "APPROVE", "FOUR_EYES_VERIFIED: Confirmed false positive match with Senior Compliance Lead."
        );
        VerificationCaseResponse response = reviewService.submitReviewDecision(caseId, validDecision);
        assertEquals(VerificationStatus.MANUALLY_APPROVED, response.status());
    }
}
