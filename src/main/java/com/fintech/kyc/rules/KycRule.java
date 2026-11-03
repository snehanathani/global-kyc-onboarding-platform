package com.fintech.kyc.rules;

import com.fintech.kyc.domain.model.VerificationCase;
import com.fintech.kyc.domain.model.VerificationCheck;

import java.util.List;

public interface KycRule {
    String getRuleId();
    String getDescription();
    int getPriority(); // lower number executed first
    boolean appliesTo(VerificationCase kycCase);
    RuleEvaluationResult evaluate(VerificationCase kycCase, List<VerificationCheck> existingChecks);
    
    default boolean isShadowMode() {
        return false;
    }
}
