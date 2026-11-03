package com.fintech.kyc.resilience;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IdentityVerificationGateway {

    private static final Logger log = LoggerFactory.getLogger(IdentityVerificationGateway.class);

    private final PrimaryIdentityVendor primaryVendor;
    private final SecondaryIdentityVendor secondaryVendor;

    public IdentityVerificationGateway(PrimaryIdentityVendor primaryVendor,
                                       SecondaryIdentityVendor secondaryVendor) {
        this.primaryVendor = primaryVendor;
        this.secondaryVendor = secondaryVendor;
    }

    @CircuitBreaker(name = "identityVendorService", fallbackMethod = "fallbackDocumentVerification")
    @Bulkhead(name = "identityVendorService")
    @Retry(name = "identityVendorService")
    public VendorClientResponse executeDocumentVerification(String customerId, String documentType, String documentId) {
        log.info("Executing Document Verification through Primary Vendor for customerId={}", customerId);
        return primaryVendor.verifyDocument(customerId, documentType, documentId);
    }

    @CircuitBreaker(name = "identityVendorService", fallbackMethod = "fallbackBiometrics")
    @Bulkhead(name = "identityVendorService")
    @Retry(name = "identityVendorService")
    public VendorClientResponse executeBiometricVerification(String customerId) {
        log.info("Executing Biometric Liveness check through Primary Vendor for customerId={}", customerId);
        return primaryVendor.verifyBiometrics(customerId);
    }

    // Resilience4j Fallback Methods
    public VendorClientResponse fallbackDocumentVerification(String customerId, String documentType,
                                                             String documentId, Throwable t) {
        log.warn("Resilience4j Circuit Breaker tripped or call failed: {}. Engaging Secondary Vendor (Veriff)", t.getMessage());
        try {
            return secondaryVendor.verifyDocumentFallback(customerId, documentType, documentId);
        } catch (Exception secondaryEx) {
            log.error("CRITICAL: Both Primary and Secondary identity vendors failed! Queuing async background processing.", secondaryEx);
            return VendorClientResponse.degraded("All identity providers temporarily unavailable; queued for background verification.");
        }
    }

    public VendorClientResponse fallbackBiometrics(String customerId, Throwable t) {
        log.warn("Resilience4j Biometrics call failed: {}. Engaging Secondary Vendor (Veriff)", t.getMessage());
        try {
            return secondaryVendor.verifyBiometricsFallback(customerId);
        } catch (Exception secondaryEx) {
            log.error("CRITICAL: Both Primary and Secondary biometric vendors failed! Queuing async check.", secondaryEx);
            return VendorClientResponse.degraded("Biometric providers temporarily unavailable; queued for async retry.");
        }
    }
}
