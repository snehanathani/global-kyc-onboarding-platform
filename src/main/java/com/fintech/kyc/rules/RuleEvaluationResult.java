package com.fintech.kyc.rules;

import com.fintech.kyc.domain.model.CheckType;
import java.util.List;

public record RuleEvaluationResult(
    String ruleId,
    boolean passed,
    boolean requiresManualReview,
    List<CheckType> requiredChecks,
    double riskScoreContribution,
    String rationale
) {
    public static RuleEvaluationResult compliant(String ruleId, String rationale) {
        return new RuleEvaluationResult(ruleId, true, false, List.of(), 0.0, rationale);
    }

    public static RuleEvaluationResult requireChecks(String ruleId, List<CheckType> checks, double riskScore, String rationale) {
        return new RuleEvaluationResult(ruleId, false, false, checks, riskScore, rationale);
    }

    public static RuleEvaluationResult escalateManualReview(String ruleId, double riskScore, String rationale) {
        return new RuleEvaluationResult(ruleId, false, true, List.of(), riskScore, rationale);
    }
}
