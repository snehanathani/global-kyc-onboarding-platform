package com.wise.kyc.outbox;

import com.wise.kyc.domain.model.CustomerType;
import com.wise.kyc.domain.model.Jurisdiction;
import com.wise.kyc.domain.model.VerificationStatus;

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
