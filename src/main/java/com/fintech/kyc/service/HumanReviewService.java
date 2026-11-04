package com.fintech.kyc.service;

import com.fintech.kyc.domain.model.CheckStatus;
import com.fintech.kyc.domain.model.CheckType;
import com.fintech.kyc.domain.model.ComplianceAuditLog;
import com.fintech.kyc.domain.model.VerificationCase;
import com.fintech.kyc.domain.model.VerificationStatus;
import com.fintech.kyc.domain.repository.ComplianceAuditLogRepository;
import com.fintech.kyc.domain.repository.VerificationCaseRepository;
import com.fintech.kyc.dto.ManualReviewDecisionRequest;
import com.fintech.kyc.dto.VerificationCaseResponse;
import com.fintech.kyc.outbox.KycEventPayload;
import com.fintech.kyc.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class HumanReviewService {

    private static final Logger log = LoggerFactory.getLogger(HumanReviewService.class);

    private final VerificationCaseRepository caseRepository;
    private final ComplianceAuditLogRepository auditRepository;
    private final OutboxService outboxService;

    public HumanReviewService(
            VerificationCaseRepository caseRepository,
            ComplianceAuditLogRepository auditRepository,
            OutboxService outboxService) {
        this.caseRepository = caseRepository;
        this.auditRepository = auditRepository;
        this.outboxService = outboxService;
    }

    public List<VerificationCaseResponse> getUnassignedReviewQueue() {
        return caseRepository.findByStatusAndAssignedAgentIdIsNull(VerificationStatus.MANUAL_REVIEW_REQUIRED)
                .stream()
                .map(VerificationCaseResponse::fromEntity)
                .toList();
    }

    @Transactional
    public VerificationCaseResponse claimCase(String caseId, String agentId) {
        VerificationCase kycCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        // Guard 1: State Precondition
        if (kycCase.getStatus() != VerificationStatus.MANUAL_REVIEW_REQUIRED) {
            throw new IllegalStateException("Only cases in MANUAL_REVIEW_REQUIRED state can be claimed. Current state: " + kycCase.getStatus());
        }

        // Guard 2: Agent Claim Collision Protection (First-claim wins)
        if (kycCase.getAssignedAgentId() != null && !kycCase.getAssignedAgentId().equals(agentId)) {
            throw new IllegalStateException("Case is already claimed by compliance officer: " + kycCase.getAssignedAgentId());
        }

        kycCase.setAssignedAgentId(agentId);
        VerificationCase saved = caseRepository.save(kycCase);

        auditRepository.save(new ComplianceAuditLog(
                caseId, agentId, "CASE_CLAIMED",
                "Compliance officer " + agentId + " claimed ownership of review case"
        ));

        return VerificationCaseResponse.fromEntity(saved);
    }

    @Transactional
    public VerificationCaseResponse submitReviewDecision(String caseId, ManualReviewDecisionRequest request) {
        VerificationCase kycCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        // Guard 1: State Precondition (Cannot decide on already closed or pending cases)
        if (kycCase.getStatus() != VerificationStatus.MANUAL_REVIEW_REQUIRED) {
            throw new IllegalStateException("Case cannot be decided: current status is " + kycCase.getStatus());
        }

        // Guard 2: Assignee Integrity (Only the assigned officer can finalize)
        if (kycCase.getAssignedAgentId() != null && !kycCase.getAssignedAgentId().equals(request.agentId())) {
            throw new IllegalStateException("Unauthorized: Case is assigned to " + kycCase.getAssignedAgentId() + ", not " + request.agentId());
        }

        // Guard 3: Sanctions Four-Eyes Requirement
        boolean hasFlaggedSanctions = kycCase.getChecks().stream()
                .anyMatch(c -> c.getCheckType() == CheckType.SANCTIONS_AND_PEP && c.getStatus() == CheckStatus.FLAGGED);

        String action = request.action().toUpperCase();
        VerificationStatus finalStatus;
        String eventType;

        if ("APPROVE".equals(action)) {
            if (hasFlaggedSanctions && !request.justificationReason().toUpperCase().contains("FOUR_EYES_VERIFIED")) {
                throw new IllegalStateException("Sanctions match override requires explicit FOUR_EYES_VERIFIED confirmation in justification.");
            }
            finalStatus = VerificationStatus.MANUALLY_APPROVED; // Distinct from AUTO_APPROVED to protect STP metrics
            eventType = "KYC_VERIFICATION_COMPLETED";
            kycCase.setRejectionReason(null);
        } else if ("REJECT".equals(action)) {
            finalStatus = VerificationStatus.REJECTED;
            eventType = "KYC_VERIFICATION_REJECTED";
            kycCase.setRejectionReason(request.justificationReason());
        } else {
            throw new IllegalArgumentException("Invalid action: " + action + ". Must be APPROVE or REJECT");
        }

        kycCase.updateStatus(finalStatus);
        kycCase.setAssignedAgentId(request.agentId());
        VerificationCase saved = caseRepository.save(kycCase);

        // Immutable compliance audit trail
        auditRepository.save(new ComplianceAuditLog(
                caseId, request.agentId(), "AGENT_DECISION_" + action,
                "Action: " + action + ". Justification: " + request.justificationReason()
        ));

        // Atomic Outbox Event Schedule (Partitioned by customerId for ordered streaming)
        KycEventPayload payload = new KycEventPayload(
                saved.getId(),
                saved.getCustomerId(),
                saved.getCustomerType(),
                saved.getJurisdiction(),
                saved.getStatus(),
                saved.getAggregateRiskScore(),
                false,
                request.justificationReason(),
                Instant.now()
        );
        outboxService.scheduleEvent("VerificationCase", saved.getCustomerId(), eventType, payload);

        log.info("Compliance officer {} finalized decision {} on case {}",
                request.agentId(), action, caseId);

        return VerificationCaseResponse.fromEntity(saved);
    }
}
