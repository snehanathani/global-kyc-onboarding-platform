package com.fintech.kyc.outbox;

import com.fintech.kyc.domain.model.CustomerType;
import com.fintech.kyc.domain.model.Jurisdiction;
import com.fintech.kyc.domain.model.VerificationStatus;

import java.time.Instant;

public record KycEventPayload(
        String caseId,
        String customerId,
        CustomerType customerType,
        Jurisdiction jurisdiction,
        VerificationStatus status,
        Double riskScore,
        boolean straightThroughProcessed,
        String rationale,
        Instant timestamp
) {}
