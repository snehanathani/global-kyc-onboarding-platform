package com.fintech.kyc.resilience;

import com.fintech.kyc.domain.model.CheckStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SanctionsVendorClient {

    private static final Logger log = LoggerFactory.getLogger(SanctionsVendorClient.class);
    private final AtomicBoolean simulateOutage = new AtomicBoolean(false);

    public void setSimulateOutage(boolean outage) {
        this.simulateOutage.set(outage);
        log.warn("Sanctions Provider simulated outage state set to: {}", outage);
    }

    public VendorClientResponse screenSanctionsAndPep(String customerId, String legalName, String country) {
        // PII Protection: Mask customer legal name in application logs
        log.info("[SANCTIONS ENGINE - ComplyAdvantage] Screening customerId='{}' in jurisdiction={}",
                customerId, country);

        if (simulateOutage.get()) {
            log.error("[SANCTIONS ENGINE] Downstream 504 Gateway Timeout while screening customerId={}", customerId);
            throw new RuntimeException("Sanctions Screening Provider 504 Gateway Timeout");
        }

        // Sanctions test cases: names containing "SANCTIONED" simulate a match
        if (legalName != null && legalName.toUpperCase().contains("SANCTIONED")) {
            log.warn("[SANCTIONS ENGINE] Watchlist match flagged for customerId={}", customerId);
            return new VendorClientResponse(
                    "COMPLY_ADVANTAGE_SANCTIONS",
                    CheckStatus.FLAGGED,
                    0.99,
                    "Exact match on OFAC / UK HMT Consolidated Financial Sanctions List",
                    false
            );
        }

        return VendorClientResponse.success(
                "COMPLY_ADVANTAGE_SANCTIONS",
                0.99,
                "Clean result across OFAC, EU, UN, and UK HMT sanctions lists. No PEP association."
        );
    }
}
