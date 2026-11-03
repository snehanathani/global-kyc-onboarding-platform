# ADR-003: Multi-tier Resilience & Vendor Failover Strategy with Resilience4j

## Status
**ACCEPTED**

## Context
Third-party identity verification, biometric liveness, and sanctions screening providers (e.g., Onfido, Veriff, ComplyAdvantage, Experian) are external SaaS dependencies prone to:
- Intermittent HTTP 502/503/504 errors.
- Latency spikes during peak hours (e.g. p99 jumping from 800ms to 12s).
- Regional outages or upstream vendor scheduled maintenance.

Without fault isolation:
- Thread pools in Spring Boot get starved waiting on slow vendor responses, causing cascading collapse across the entire onboarding microservice.
- Prospective customers face onboarding timeouts, drop out of the conversion funnel, and lose trust in the service.

## Decision
We implement a **Multi-Tier Fault Tolerance Architecture** utilizing **Resilience4j**:
1. **Circuit Breaker (`@CircuitBreaker`)**:
   - Sliding window of 10 calls with a 50% failure rate threshold.
   - When opened, fast-fails further requests without hitting the broken upstream API for 8,000ms.
   - Half-Open probe state admits 3 trial calls to test upstream recovery before returning to `CLOSED`.
2. **Multi-Tier Active Failover**:
   - Primary Vendor: Onfido.
   - Secondary Fallback Vendor: Veriff.
   - If the Primary vendor circuit trips or times out, traffic seamlessly fails over to the Secondary vendor within the same user transaction.
3. **Graceful Degradation (`PENDING_VENDOR`)**:
   - If both Primary and Secondary vendors fail (catastrophic multi-vendor outage), the system logs the degraded state, flags the verification check as `PENDING_VENDOR`, and queues an asynchronous background worker.
   - The customer's signup session does NOT throw a 500 error; they receive an informative status: *"We are finalizing your verification in the background; you will be notified shortly."*
4. **Bulkhead (`@Bulkhead`)**:
   - Limits concurrent external vendor invocations to 10 per instance, isolating vendor latency from core HTTP request threads.
5. **Exponential Backoff Retry (`@Retry`)**:
   - Configured for transient network exceptions with jitter to avoid the thundering herd problem.

## Consequences
### Positive:
- **Resilient Conversion Rates**: Customers continue onboarding even during major SaaS vendor outages.
- **Failover Transparency**: Ops and metrics dashboards immediately see `kyc.vendor.fallback_engaged` metrics.
- **Thread Isolation**: Eliminates cascading thread exhaustion.

### Negative / Trade-offs:
- Fallback vendor (Veriff) may incur slightly different per-check API costs.
- Requires maintaining contracts and adapter integrations for multiple external KYC vendors.
