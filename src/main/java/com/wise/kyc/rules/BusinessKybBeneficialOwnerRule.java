package com.wise.kyc.rules;

import com.wise.kyc.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BusinessKybBeneficialOwnerRule implements KycRule {

    @Override
    public String getRuleId() {
        return "RULE_GLOBAL_BUSINESS_KYB";
    }

    @Override
    public String getDescription() {
        return "Global KYB: Commercial Registry confirmation and Ultimate Beneficial Ownership (UBO >= 25%) verification";
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public boolean appliesTo(VerificationCase kycCase) {
        return kycCase.getCustomerType() == CustomerType.BUSINESS;
    }

    @Override
    public RuleEvaluationResult evaluate(VerificationCase kycCase, List<VerificationCheck> existingChecks) {
        List<CheckType> missingChecks = new ArrayList<>();

        boolean hasRegistryCheck = existingChecks.stream()
                .anyMatch(c -> c.getCheckType() == CheckType.BUSINESS_REGISTRY && c.getStatus() == CheckStatus.PASSED);
        boolean hasUboCheck = existingChecks.stream()
                .anyMatch(c -> c.getCheckType() == CheckType.UBO_VERIFICATION && c.getStatus() == CheckStatus.PASSED);

        if (!hasRegistryCheck) missingChecks.add(CheckType.BUSINESS_REGISTRY);
        if (!hasUboCheck) missingChecks.add(CheckType.UBO_VERIFICATION);

        if (missingChecks.isEmpty()) {
            return RuleEvaluationResult.compliant(getRuleId(), "Business KYB & UBO verifications satisfied.");
        }

        return RuleEvaluationResult.requireChecks(getRuleId(), missingChecks, 0.25,
                "Business onboarding mandates registry and UBO checks: " + missingChecks);
    }
}
