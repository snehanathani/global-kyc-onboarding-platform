# ADR-005: Straight-Through Processing (STP) vs Human-in-the-Loop Operational Triaging

## Status
**ACCEPTED**

## Context
Wise onboards hundreds of thousands of customers monthly. Manual verification by human compliance officers takes between 10 minutes to several hours, carries high labor costs, and creates severe onboarding friction that destroys signup conversion rates.
However, financial institutions are legally required to prevent money laundering, sanctions evasion, and fraud under penalty of severe fines and license revocation.

Balancing customer velocity with regulatory compliance requires a principled decision model.

## Decision
We implement a **Dual-Path Straight-Through Processing (STP) Architecture**:
1. **Automated Straight-Through Processing (STP)**:
   - Evaluated within the synchronous API transaction (target latency < 500ms).
   - Qualifications:
     - All mandatory checks (`IDENTITY_DOCUMENT`, `BIOMETRIC_LIVENESS`, `SANCTIONS_AND_PEP`) return `PASSED` or `FALLBACK_VENDOR_USED`.
     - Vendor confidence score meets or exceeds confidence threshold ($\ge 0.85$).
     - Sanctions and PEP screening returns clean with zero matches.
     - Expected annual transfer volume is below enhanced due diligence escalation thresholds ($< £50,000$).
   - Result: Immediate transition to `AUTO_APPROVED`, `straightThroughProcessed = true`, event `KYC_VERIFICATION_COMPLETED` emitted.
2. **Human-in-the-Loop Triage (Manual Review Escalation)**:
   - Triggered when:
     - Sanctions or PEP screening indicates potential name match (OFAC/HMT).
     - Document confidence is borderline ($< 0.85$) or image quality is degraded.
     - Customer volume exceeds high-risk thresholds ($> £50,000$).
     - Business entity ownership structure is complex ($> 4$ UBO tiers).
   - Result: Case transitions to `MANUAL_REVIEW_REQUIRED`, prioritized in the compliance review queue, event `KYC_MANUAL_REVIEW_REQUIRED` emitted to Kafka topic `kyc.agent.review_required`.
   - Dedicated Compliance Agent REST API enables compliance officers to inspect flagged risk factors, claim ownership, and submit audited approval/rejection decisions with mandatory rationale.

## Consequences
### Positive:
- **Scalable Unit Economics**: Over 85% of legitimate personal customers are approved instantly without human operational overhead.
- **Risk Mitigation**: High-risk actors and sanctions matches are systematically quarantined for human review.
- **SLA Observability**: Metrics dashboard tracks STP rate (`kyc.verifications.auto_approved` / `kyc.verifications.initiated`) in real time.

### Negative / Trade-offs:
- Manual review queues require operational capacity planning and SLA monitoring to prevent backlogs during signup surges.
