package com.wise.kyc.service;

import com.wise.kyc.domain.model.ComplianceAuditLog;
import com.wise.kyc.domain.model.VerificationCase;
import com.wise.kyc.domain.model.VerificationStatus;
import com.wise.kyc.domain.repository.ComplianceAuditLogRepository;
import com.wise.kyc.domain.repository.VerificationCaseRepository;
import com.wise.kyc.dto.ManualReviewDecisionRequest;
import com.wise.kyc.dto.VerificationCaseResponse;
import com.wise.kyc.outbox.KycEventPayload;
import com.wise.kyc.outbox.OutboxService;
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

        kycCase.setAssignedAgentId(agentId);
        VerificationCase saved = caseRepository.save(kycCase);

        auditRepository.save(new ComplianceAuditLog(
                caseId, agentId, "CASE_CLAIMED",
                "Compliance officer " + agentId + " claimed case for investigation"
        ));

        return VerificationCaseResponse.fromEntity(saved);
    }

    @Transactional
    public VerificationCaseResponse submitReviewDecision(String caseId, ManualReviewDecisionRequest request) {
        VerificationCase kycCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        String action = request.action().toUpperCase();
        VerificationStatus finalStatus;
        String eventType;

        if ("APPROVE".equals(action)) {
            finalStatus = VerificationStatus.AUTO_APPROVED; // Overridden to approved
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
                "Justification: " + request.justificationReason()
        ));

        // Atomic Outbox Event Schedule
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
        outboxService.scheduleEvent("VerificationCase", saved.getId(), eventType, payload);

        log.info("Compliance officer {} finalized decision {} on case {}",
                request.agentId(), action, caseId);

        return VerificationCaseResponse.fromEntity(saved);
    }
}
