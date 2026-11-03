package com.fintech.kyc.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualReviewDecisionRequest(
        @NotBlank(message = "Agent ID is required")
        String agentId,

        @NotBlank(message = "Action is required (APPROVE or REJECT)")
        String action, // "APPROVE" or "REJECT"

        @NotBlank(message = "Regulatory compliance justification reason is required")
        String justificationReason
) {}
