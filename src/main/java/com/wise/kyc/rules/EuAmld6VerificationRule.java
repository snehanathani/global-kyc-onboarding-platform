package com.wise.kyc.rules;

import com.wise.kyc.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EuAmld6VerificationRule implements KycRule {

    @Override
    public String getRuleId() {
        return "RULE_EU_AMLD6_COMPLIANCE";
    }

    @Override
    public String getDescription() {
        return "EU 6th Anti-Money Laundering Directive: Biometric Liveness + Identity Document + Real-time PEP screening";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean appliesTo(VerificationCase kycCase) {
        return kycCase.getJurisdiction() == Jurisdiction.EU;
    }

    @Override
    public RuleEvaluationResult evaluate(VerificationCase kycCase, List<VerificationCheck> existingChecks) {
        List<CheckType> missingChecks = new ArrayList<>();

        boolean hasIdentity = existingChecks.stream()
                .anyMatch(c -> c.getCheckType() == CheckType.IDENTITY_DOCUMENT && c.getStatus() == CheckStatus.PASSED);
        boolean hasBiometric = existingChecks.stream()
                .anyMatch(c -> c.getCheckType() == CheckType.BIOMETRIC_LIVENESS && c.getStatus() == CheckStatus.PASSED);
        boolean hasSanctions = existingChecks.stream()
                .anyMatch(c -> c.getCheckType() == CheckType.SANCTIONS_AND_PEP && c.getStatus() == CheckStatus.PASSED);

        if (!hasIdentity) missingChecks.add(CheckType.IDENTITY_DOCUMENT);
        if (!hasBiometric) missingChecks.add(CheckType.BIOMETRIC_LIVENESS);
        if (!hasSanctions) missingChecks.add(CheckType.SANCTIONS_AND_PEP);

        if (missingChecks.isEmpty()) {
            return RuleEvaluationResult.compliant(getRuleId(), "EU AMLD6 requirements fully satisfied.");
        }

        return RuleEvaluationResult.requireChecks(getRuleId(), missingChecks, 0.20,
                "EU AMLD6 mandates biometric and identity verification: " + missingChecks);
    }
}
