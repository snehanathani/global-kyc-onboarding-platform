package com.wise.kyc.rules;

import com.wise.kyc.domain.model.VerificationCase;
import com.wise.kyc.domain.model.VerificationCheck;

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
