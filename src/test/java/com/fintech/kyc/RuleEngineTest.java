package com.fintech.kyc;

import com.fintech.kyc.domain.model.*;
import com.fintech.kyc.rules.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private DynamicRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        List<KycRule> rules = List.of(
                new UkFcaVerificationRule(),
                new EuAmld6VerificationRule(),
                new BusinessKybBeneficialOwnerRule(),
                new HighRiskVolumeRule()
        );
        ruleEngine = new DynamicRuleEngine(rules);
    }

    @Test
    @DisplayName("UK Individual with low volume requires Identity Document and Sanctions screening")
    void testUkIndividualLowVolume() {
        VerificationCase kycCase = new VerificationCase(
                "cust-uk-101",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "John Watson",
                null,
                new BigDecimal("2500.00")
        );

        DynamicRuleEngine.EngineDecisionOutcome outcome = ruleEngine.evaluateCase(kycCase, List.of());

        assertFalse(outcome.fullyCompliant());
        assertFalse(outcome.requiresManualReview());
        assertTrue(outcome.pendingChecksToExecute().contains(CheckType.IDENTITY_DOCUMENT));
        assertTrue(outcome.pendingChecksToExecute().contains(CheckType.SANCTIONS_AND_PEP));
        assertFalse(outcome.pendingChecksToExecute().contains(CheckType.PROOF_OF_ADDRESS));
    }

    @Test
    @DisplayName("UK Individual with high volume (> GBP 10,000) triggers Enhanced Due Diligence (Proof of Address)")
    void testUkIndividualEnhancedDueDiligence() {
        VerificationCase kycCase = new VerificationCase(
                "cust-uk-102",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "Sherlock Holmes",
                null,
                new BigDecimal("15000.00")
        );

        DynamicRuleEngine.EngineDecisionOutcome outcome = ruleEngine.evaluateCase(kycCase, List.of());

        assertTrue(outcome.pendingChecksToExecute().contains(CheckType.PROOF_OF_ADDRESS));
    }

    @Test
    @DisplayName("EU AMLD6 mandates Biometric Liveness check in addition to Document & Sanctions")
    void testEuAmld6BiometricRequirement() {
        VerificationCase kycCase = new VerificationCase(
                "cust-eu-201",
                CustomerType.INDIVIDUAL,
                Jurisdiction.EU,
                "Marie Curie",
                null,
                new BigDecimal("4000.00")
        );

        DynamicRuleEngine.EngineDecisionOutcome outcome = ruleEngine.evaluateCase(kycCase, List.of());

        assertTrue(outcome.pendingChecksToExecute().contains(CheckType.BIOMETRIC_LIVENESS));
        assertTrue(outcome.pendingChecksToExecute().contains(CheckType.IDENTITY_DOCUMENT));
        assertTrue(outcome.pendingChecksToExecute().contains(CheckType.SANCTIONS_AND_PEP));
    }

    @Test
    @DisplayName("Global Business onboarding mandates Business Registry and UBO checks")
    void testBusinessKybRequirements() {
        VerificationCase kycCase = new VerificationCase(
                "biz-uk-301",
                CustomerType.BUSINESS,
                Jurisdiction.GB,
                "Baker Street Consulting Ltd",
                "12345678",
                new BigDecimal("25000.00")
        );

        DynamicRuleEngine.EngineDecisionOutcome outcome = ruleEngine.evaluateCase(kycCase, List.of());

        assertTrue(outcome.pendingChecksToExecute().contains(CheckType.BUSINESS_REGISTRY));
        assertTrue(outcome.pendingChecksToExecute().contains(CheckType.UBO_VERIFICATION));
    }

    @Test
    @DisplayName("Volume exceeding GBP 50,000 automatically escalates to compliance officer manual review")
    void testHighRiskVolumeEscalation() {
        VerificationCase kycCase = new VerificationCase(
                "cust-high-risk-401",
                CustomerType.INDIVIDUAL,
                Jurisdiction.GB,
                "High Roller",
                null,
                new BigDecimal("75000.00")
        );

        DynamicRuleEngine.EngineDecisionOutcome outcome = ruleEngine.evaluateCase(kycCase, List.of());

        assertTrue(outcome.requiresManualReview());
        assertTrue(outcome.aggregateRiskScore() >= 0.40);
    }
}
