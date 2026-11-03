package com.fintech.kyc.rules;

import com.fintech.kyc.domain.model.CheckType;
import com.fintech.kyc.domain.model.VerificationCase;
import com.fintech.kyc.domain.model.VerificationCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DynamicRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(DynamicRuleEngine.class);

    private final List<KycRule> registeredRules;

    public DynamicRuleEngine(List<KycRule> registeredRules) {
        // Sort rules by priority ascending
        this.registeredRules = registeredRules.stream()
                .sorted(Comparator.comparingInt(KycRule::getPriority))
                .toList();
    }

    public EngineDecisionOutcome evaluateCase(VerificationCase kycCase, List<VerificationCheck> existingChecks) {
        log.info("Evaluating KYC Case {} ({}) in jurisdiction {}",
                kycCase.getId(), kycCase.getCustomerType(), kycCase.getJurisdiction());

        Set<CheckType> requiredChecks = new LinkedHashSet<>();
        boolean requiresManualReview = false;
        double aggregateRiskScore = 0.0;
        List<String> rationales = new ArrayList<>();

        for (KycRule rule : registeredRules) {
            if (rule.appliesTo(kycCase)) {
                RuleEvaluationResult result = rule.evaluate(kycCase, existingChecks);
                log.debug("Rule {} result: passed={}, manualReview={}, checks={}",
                        rule.getRuleId(), result.passed(), result.requiresManualReview(), result.requiredChecks());

                if (rule.isShadowMode()) {
                    log.info("[SHADOW MODE RULE] {} evaluated: {}", rule.getRuleId(), result);
                    continue; // Shadow rules do not alter real production outcome
                }

                if (!result.passed()) {
                    requiredChecks.addAll(result.requiredChecks());
                    rationales.add(result.rationale());
                }

                if (result.requiresManualReview()) {
                    requiresManualReview = true;
                    rationales.add(result.rationale());
                }

                aggregateRiskScore += result.riskScoreContribution();
            }
        }

        // Cap aggregate risk score between 0.0 and 1.0
        double normalizedRisk = Math.min(1.0, Math.max(0.0, aggregateRiskScore));

        boolean fullyCompliant = requiredChecks.isEmpty() && !requiresManualReview;

        return new EngineDecisionOutcome(
                fullyCompliant,
                requiresManualReview,
                new ArrayList<>(requiredChecks),
                normalizedRisk,
                rationales
        );
    }

    public record EngineDecisionOutcome(
            boolean fullyCompliant,
            boolean requiresManualReview,
            List<CheckType> pendingChecksToExecute,
            double aggregateRiskScore,
            List<String> auditRationales
    ) {}
}
