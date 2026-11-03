package com.fintech.kyc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compliance_audit_logs", indexes = {
    @Index(name = "idx_audit_case", columnList = "caseId"),
    @Index(name = "idx_audit_action", columnList = "action")
})
public class ComplianceAuditLog {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String actorId; // system, agent_id, or compliance_officer

    @Column(nullable = false)
    private String action; // e.g. CASE_CREATED, AUTO_APPROVED, MANUAL_REVIEW_APPROVED, RULE_EVALUATED

    @Column(length = 4000)
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    public ComplianceAuditLog() {
    }

    public ComplianceAuditLog(String caseId, String actorId, String action, String details) {
        this.id = UUID.randomUUID().toString();
        this.caseId = caseId;
        this.actorId = actorId;
        this.action = action;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public String getCaseId() { return caseId; }
    public String getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public Instant getTimestamp() { return timestamp; }
}
