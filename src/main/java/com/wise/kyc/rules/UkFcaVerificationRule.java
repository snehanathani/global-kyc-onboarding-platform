package com.wise.kyc.rules;

import com.wise.kyc.domain.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class UkFcaVerificationRule implements KycRule {

    private static final BigDecimal ENHANCED_DUE_DILIGENCE_THRESHOLD = new BigDecimal("10000.00");

    @Override
    public String getRuleId() {
        return "RULE_UK_FCA_COMPLIANCE";
    }

    @Override
    public String getDescription() {
        return "UK FCA MLR 2017: Customer Due Diligence (ID + Sanctions, plus Proof of Address for volume > £10,000)";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean appliesTo(VerificationCase kycCase) {
        return kycCase.getJurisdiction() == Jurisdiction.GB;
    }

    @Override
    public RuleEvaluationResult evaluate(VerificationCase kycCase, List<VerificationCheck> existingChecks) {
        List<CheckType> missingChecks = new ArrayList<>();

        boolean hasIdentityCheck = existingChecks.stream()
                .anyMatch(c -> c.getCheckType() == CheckType.IDENTITY_DOCUMENT && c.getStatus() == CheckStatus.PASSED);
        boolean hasSanctionsCheck = existingChecks.stream()
                .anyMatch(c -> c.getCheckType() == CheckType.SANCTIONS_AND_PEP && c.getStatus() == CheckStatus.PASSED);

        if (!hasIdentityCheck) {
            missingChecks.add(CheckType.IDENTITY_DOCUMENT);
        }
        if (!hasSanctionsCheck) {
            missingChecks.add(CheckType.SANCTIONS_AND_PEP);
        }

        // Enhanced due diligence threshold for large corridor volume
        if (kycCase.getExpectedAnnualVolumeGbp().compareTo(ENHANCED_DUE_DILIGENCE_THRESHOLD) >= 0) {
            boolean hasPoa = existingChecks.stream()
                    .anyMatch(c -> c.getCheckType() == CheckType.PROOF_OF_ADDRESS && c.getStatus() == CheckStatus.PASSED);
            if (!hasPoa) {
                missingChecks.add(CheckType.PROOF_OF_ADDRESS);
            }
        }

        if (missingChecks.isEmpty()) {
            return RuleEvaluationResult.compliant(getRuleId(), "UK FCA requirements satisfied with full CDD/EDD verification.");
        }

        return RuleEvaluationResult.requireChecks(getRuleId(), missingChecks, 0.15,
                "UK FCA compliance mandates: " + missingChecks);
    }
}
