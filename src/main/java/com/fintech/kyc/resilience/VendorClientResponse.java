package com.fintech.kyc.resilience;

import com.fintech.kyc.domain.model.CheckStatus;

public record VendorClientResponse(
        String vendorName,
        CheckStatus status,
        Double confidenceScore,
        String rawResultDetails,
        boolean fallbackEngaged
) {
    public static VendorClientResponse success(String vendor, Double confidence, String details) {
        return new VendorClientResponse(vendor, CheckStatus.PASSED, confidence, details, false);
    }

    public static VendorClientResponse fallbackSuccess(String vendor, Double confidence, String details) {
        return new VendorClientResponse(vendor, CheckStatus.FALLBACK_VENDOR_USED, confidence, details, true);
    }

    public static VendorClientResponse degraded(String details) {
        return new VendorClientResponse("INTERNAL_ASYNC_QUEUE", CheckStatus.PENDING_VENDOR, 0.0, details, true);
    }
}
