package com.fintech.kyc.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PrimaryIdentityVendor {

    private static final Logger log = LoggerFactory.getLogger(PrimaryIdentityVendor.class);
    private final AtomicBoolean simulateOutage = new AtomicBoolean(false);

    public void setSimulateOutage(boolean outage) {
        this.simulateOutage.set(outage);
        log.warn("Primary Identity Vendor (Onfido) simulated outage state changed to: {}", outage);
    }

    public boolean isOutageSimulated() {
        return simulateOutage.get();
    }

    public VendorClientResponse verifyDocument(String customerId, String documentType, String documentId) {
        log.info("[PRIMARY VENDOR - Onfido] Processing document verification for customerId={}", customerId);

        if (simulateOutage.get()) {
            log.error("[PRIMARY VENDOR - Onfido] Downstream HTTP 503 Service Unavailable / Gateway Timeout");
            throw new RuntimeException("Onfido API 503: Service Unavailable");
        }

        // Simulating successful vendor verification
        return VendorClientResponse.success(
                "ONFIDO_PRIMARY",
                0.97,
                "Document authenticity verified. NFC cryptographically validated. MRZ valid."
        );
    }

    public VendorClientResponse verifyBiometrics(String customerId) {
        log.info("[PRIMARY VENDOR - Onfido] Processing 3D biometric liveness check for customerId={}", customerId);

        if (simulateOutage.get()) {
            throw new RuntimeException("Onfido Biometric API 504: Gateway Timeout");
        }

        return VendorClientResponse.success(
                "ONFIDO_PRIMARY",
                0.95,
                "3D passive liveness confirmed. Spoofing confidence 0.01."
        );
    }
}
