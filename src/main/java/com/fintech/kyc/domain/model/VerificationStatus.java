package com.fintech.kyc.domain.model;

public enum VerificationStatus {
    PENDING,
    IN_PROGRESS,
    AUTO_APPROVED,
    MANUAL_REVIEW_REQUIRED,
    REJECTED,
    EXPIRED
}
