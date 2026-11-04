package com.fintech.kyc.rules;

import com.fintech.kyc.domain.model.VerificationCase;
import com.fintech.kyc.domain.model.VerificationCheck;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class HighRiskVolumeRule implements KycRule {

    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("50000.00");

    @Override
    public String getRuleId() {
        return "RULE_HIGH_RISK_VOLUME_ESCALATION";
    }

    @Override
    public String getDescription() {
        return "Escalates cases exceeding GBP 50,000 annual transfer volume directly to Compliance Senior Review";
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean appliesTo(VerificationCase kycCase) {
        return kycCase.getExpectedAnnualVolumeGbp() != null &&
               kycCase.getExpectedAnnualVolumeGbp().compareTo(HIGH_RISK_THRESHOLD) > 0;
    }

    @Override
    public RuleEvaluationResult evaluate(VerificationCase kycCase, List<VerificationCheck> existingChecks) {
        return RuleEvaluationResult.escalateManualReview(
                getRuleId(),
                0.40,
                "High transfer volume (> GBP 50,000) mandates enhanced compliance oversight and manual verification"
        );
    }
}
