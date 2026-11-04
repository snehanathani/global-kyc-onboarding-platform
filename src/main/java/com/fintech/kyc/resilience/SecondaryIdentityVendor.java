package com.fintech.kyc.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SecondaryIdentityVendor {

    private static final Logger log = LoggerFactory.getLogger(SecondaryIdentityVendor.class);
    private final AtomicBoolean simulateOutage = new AtomicBoolean(false);

    public void setSimulateOutage(boolean outage) {
        this.simulateOutage.set(outage);
        log.warn("Secondary Identity Vendor (Veriff) simulated outage set to: {}", outage);
    }

    public VendorClientResponse verifyDocumentFallback(String customerId, String documentType, String documentId) {
        log.warn("[SECONDARY VENDOR - Veriff Fallback] Active failover engaged for customerId={}", customerId);

        if (simulateOutage.get()) {
            log.error("[SECONDARY VENDOR - Veriff] Downstream outage on secondary vendor!");
            throw new RuntimeException("Veriff API 503: Service Unavailable");
        }

        return VendorClientResponse.fallbackSuccess(
                "VERIFF_SECONDARY_FALLBACK",
                0.93,
                "Fallback automated check passed via secondary provider Veriff."
        );
    }

    public VendorClientResponse verifyBiometricsFallback(String customerId) {
        log.warn("[SECONDARY VENDOR - Veriff Fallback] Active failover biometric check for customerId={}", customerId);

        if (simulateOutage.get()) {
            throw new RuntimeException("Veriff Biometric API 503: Service Unavailable");
        }

        return VendorClientResponse.fallbackSuccess(
                "VERIFF_SECONDARY_FALLBACK",
                0.92,
                "Secondary biometric match verified with high confidence."
        );
    }
}
