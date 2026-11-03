package com.wise.kyc;

import com.wise.kyc.domain.model.CheckStatus;
import com.wise.kyc.resilience.IdentityVerificationGateway;
import com.wise.kyc.resilience.PrimaryIdentityVendor;
import com.wise.kyc.resilience.VendorClientResponse;
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

    @AfterEach
    void tearDown() {
        primaryVendor.setSimulateOutage(false);
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
}
