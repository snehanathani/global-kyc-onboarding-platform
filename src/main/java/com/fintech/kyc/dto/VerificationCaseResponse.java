package com.fintech.kyc.dto;

import com.fintech.kyc.domain.model.CustomerType;
import com.fintech.kyc.domain.model.Jurisdiction;
import com.fintech.kyc.domain.model.VerificationCase;
import com.fintech.kyc.domain.model.VerificationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record VerificationCaseResponse(
        String id,
        String customerId,
        CustomerType customerType,
        Jurisdiction jurisdiction,
        String legalName,
        VerificationStatus status,
        Double aggregateRiskScore,
        Boolean straightThroughProcessed,
        String assignedAgentId,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        List<CheckSummaryResponse> checks
) {
    public record CheckSummaryResponse(
            String checkType,
            String status,
            String vendorName,
            Double confidenceScore,
            String details
    ) {}

    public static VerificationCaseResponse fromEntity(VerificationCase entity) {
        List<CheckSummaryResponse> checks = entity.getChecks().stream()
                .map(c -> new CheckSummaryResponse(
                        c.getCheckType().name(),
                        c.getStatus().name(),
                        c.getVendorName(),
                        c.getConfidenceScore(),
                        c.getDetails()
                )).toList();

        return new VerificationCaseResponse(
                entity.getId(),
                entity.getCustomerId(),
                entity.getCustomerType(),
                entity.getJurisdiction(),
                entity.getLegalName(),
                entity.getStatus(),
                entity.getAggregateRiskScore(),
                entity.getStraightThroughProcessed(),
                entity.getAssignedAgentId(),
                entity.getRejectionReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                checks
        );
    }
}
