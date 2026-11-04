package com.fintech.kyc;

import com.fintech.kyc.domain.model.CheckStatus;
import com.fintech.kyc.resilience.IdentityVerificationGateway;
import com.fintech.kyc.resilience.PrimaryIdentityVendor;
import com.fintech.kyc.resilience.SecondaryIdentityVendor;
import com.fintech.kyc.resilience.VendorClientResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CircuitBreakerIntegrationTest {

    @Autowired
    private IdentityVerificationGateway identityGateway;

    @Autowired
    private PrimaryIdentityVendor primaryVendor;

    @Autowired
    private SecondaryIdentityVendor secondaryVendor;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void tearDown() {
        primaryVendor.setSimulateOutage(false);
        secondaryVendor.setSimulateOutage(false);
        if (circuitBreakerRegistry.find("identityVendorService").isPresent()) {
            circuitBreakerRegistry.circuitBreaker("identityVendorService").reset();
        }
    }

    @Test
    @DisplayName("Primary vendor handles document verification when healthy")
    void testPrimaryVendorSuccess() {
        primaryVendor.setSimulateOutage(false);

        VendorClientResponse response = identityGateway.executeDocumentVerification(
                "cust-healthy-1", "PASSPORT", "UK-PASS-9988");

        assertNotNull(response);
        assertEquals("ONFIDO_PRIMARY", response.vendorName());
        assertEquals(CheckStatus.PASSED, response.status());
        assertFalse(response.fallbackEngaged());
    }

    @Test
    @DisplayName("Circuit Breaker triggers failover to Secondary Vendor (Veriff) when Primary fails")
    void testFailoverToSecondaryVendor() {
        primaryVendor.setSimulateOutage(true);

        VendorClientResponse response = identityGateway.executeDocumentVerification(
                "cust-resilience-2", "PASSPORT", "UK-PASS-1122");

        assertNotNull(response);
        assertEquals("VERIFF_SECONDARY_FALLBACK", response.vendorName());
        assertEquals(CheckStatus.FALLBACK_VENDOR_USED, response.status());
        assertTrue(response.fallbackEngaged());
    }

    @Test
    @DisplayName("Catastrophic Outage: When both vendors fail, status degrades to PENDING_VENDOR (never 500)")
    void testBothVendorsOutageGracefulDegradation() {
        primaryVendor.setSimulateOutage(true);
        secondaryVendor.setSimulateOutage(true);

        VendorClientResponse response = identityGateway.executeDocumentVerification(
                "cust-blackout-3", "PASSPORT", "UK-PASS-0000");

        assertNotNull(response);
        assertEquals("INTERNAL_ASYNC_QUEUE", response.vendorName());
        assertEquals(CheckStatus.PENDING_VENDOR, response.status());
        assertTrue(response.fallbackEngaged());
    }
}
