package com.fintech.kyc.dto;

import com.fintech.kyc.domain.model.CustomerType;
import com.fintech.kyc.domain.model.Jurisdiction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InitiateVerificationRequest(
        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotNull(message = "Customer type is required")
        CustomerType customerType,

        @NotNull(message = "Jurisdiction is required")
        Jurisdiction jurisdiction,

        @NotBlank(message = "Legal name is required")
        String legalName,

        String registrationOrTaxId,

        BigDecimal expectedAnnualVolumeGbp,

        String documentType, // e.g. "PASSPORT", "NATIONAL_ID"
        String documentId,

        String idempotencyKey
) {}
