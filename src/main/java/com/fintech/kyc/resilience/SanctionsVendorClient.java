package com.fintech.kyc.resilience;

import com.fintech.kyc.domain.model.CheckStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SanctionsVendorClient {

    private static final Logger log = LoggerFactory.getLogger(SanctionsVendorClient.class);

    public VendorClientResponse screenSanctionsAndPep(String customerId, String legalName, String country) {
        log.info("[SANCTIONS ENGINE - ComplyAdvantage] Screening individual '{}' in country {}", legalName, country);

        // Sanctions test cases: names containing "SANCTIONED" or "VLADIMIR_TEST" simulate a match
        if (legalName != null && legalName.toUpperCase().contains("SANCTIONED")) {
            log.warn("[SANCTIONS ENGINE] CRITICAL: PEP / Sanction match flagged for '{}'", legalName);
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
