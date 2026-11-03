package com.fintech.kyc.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class KycMetricsService {

    private final Counter verificationsInitiated;
    private final Counter autoApprovedCount;
    private final Counter manualReviewCount;
    private final Counter circuitBreakerFallbackEngaged;
    private final Timer verificationLatencyTimer;

    public KycMetricsService(MeterRegistry registry) {
        this.verificationsInitiated = Counter.builder("kyc.verifications.initiated")
                .description("Total number of KYC verification cases initiated")
                .register(registry);

        this.autoApprovedCount = Counter.builder("kyc.verifications.auto_approved")
                .description("Number of straight-through processed auto-approved cases")
                .register(registry);

        this.manualReviewCount = Counter.builder("kyc.verifications.manual_review")
                .description("Number of cases escalated to compliance officer review")
                .register(registry);

        this.circuitBreakerFallbackEngaged = Counter.builder("kyc.vendor.fallback_engaged")
                .description("Number of times circuit breaker fallback vendor was engaged")
                .register(registry);

        this.verificationLatencyTimer = Timer.builder("kyc.verification.duration")
                .description("Latency of end-to-end KYC verification decision")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void incrementInitiated() { verificationsInitiated.increment(); }
    public void incrementAutoApproved() { autoApprovedCount.increment(); }
    public void incrementManualReview() { manualReviewCount.increment(); }
    public void incrementFallbackEngaged() { circuitBreakerFallbackEngaged.increment(); }
    public void recordDuration(long durationMillis) {
        verificationLatencyTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }
}
