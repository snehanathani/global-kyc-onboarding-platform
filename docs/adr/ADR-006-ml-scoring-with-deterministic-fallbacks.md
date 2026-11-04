# ADR-006: ML Scoring with Deterministic Safe Fallbacks

## Status
**ACCEPTED**

## Context
Machine Learning (ML) inference models (such as document authenticity verification, biometric spoofing detection, and behavioral risk scoring) can significantly improve straight-through processing rates and catch sophisticated forgery.
However, in high-stakes financial compliance:
- ML models are inherently probabilistic; false negatives can lead to onboarding sanctioned individuals or fraudulent entities.
- ML models can suffer from concept drift, adversarial evasion attacks, and unpredictable latency spikes during cold starts.
- Regulators (e.g. UK FCA, EU BaFin) require explainability and deterministic audit trails for adverse onboarding decisions.

## Decision
We implement a **Dual-Tier Decision Architecture** combining deterministic compliance rules with ML scoring:

1. **Deterministic Rule Supremacy**:
   - Regulatory requirements (such as PEP/Sanctions screening, mandatory registry checks, and threshold-based proof of address) are always enforced deterministically via the `DynamicRuleEngine`.
   - An ML score can never override or bypass a failed regulatory rule or a sanctions hit.

2. **Shadow Mode Model Validation**:
   - New or updated ML models execute in **Shadow Mode** on production traffic for a calibration period (typically 30 days).
   - In shadow mode, the model computes predictions and logs risk scores, but does not alter customer onboarding outcomes.
   - Ground truth is calibrated against subsequent manual compliance officer review outcomes.

3. **Strict Confidence Gating**:
   - Straight-through processing is only granted if the model confidence score meets or exceeds a strict threshold ($\ge 0.90$) and all deterministic rules pass.
   - Any borderline score ($0.70 \le \text{confidence} < 0.90$) routes the case to the `HumanReviewService` queue with the model's top contributing risk factors highlighted.

4. **Graceful Fail-Safe to Rules**:
   - If the ML inference service times out ($> 400\text{ms}$), throws a 5xx error, or the circuit breaker trips, the system immediately falls back to standard deterministic rule evaluation without blocking user onboarding.
   - If deterministic rules are inconclusive without the ML check, the case fails safely to `MANUAL_REVIEW_REQUIRED`.

5. **Explainability & Model Auditing**:
   - The model version ID, inference latency, confidence score, and input feature hash are persisted immutably in `compliance_audit_logs`.

## Consequences
### Positive:
- **Zero Regulatory Degradation**: Probabilistic models cannot accidentally approve sanctioned or non-compliant users.
- **Resilient Operations**: Model outages or inference timeouts never bring down user onboarding.
- **Audit Compliance**: Regulatory inquiries can reconstruct the exact model version and features used for any historical verification.

### Negative / Trade-offs:
- Requires maintaining dual evaluation paths (ML scoring pipeline alongside deterministic rule DAG).
- Shadow mode evaluation consumes compute resources for models not yet actively filtering production traffic.
