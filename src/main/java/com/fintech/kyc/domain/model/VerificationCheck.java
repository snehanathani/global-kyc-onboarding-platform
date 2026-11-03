package com.fintech.kyc.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_checks", indexes = {
    @Index(name = "idx_check_type", columnList = "checkType"),
    @Index(name = "idx_check_status", columnList = "status")
})
public class VerificationCheck {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckStatus status;

    private String vendorName;

    private Double confidenceScore;

    @Column(length = 2000)
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    @JsonIgnore
    private VerificationCase verificationCase;

    public VerificationCheck() {
    }

    public VerificationCheck(CheckType checkType, String vendorName) {
        this.id = UUID.randomUUID().toString();
        this.checkType = checkType;
        this.vendorName = vendorName;
        this.status = CheckStatus.PENDING_VENDOR;
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public CheckType getCheckType() { return checkType; }
    public void setCheckType(CheckType checkType) { this.checkType = checkType; }

    public CheckStatus getStatus() { return status; }
    public void setStatus(CheckStatus status) { this.status = status; }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public VerificationCase getVerificationCase() { return verificationCase; }
    public void setVerificationCase(VerificationCase verificationCase) { this.verificationCase = verificationCase; }
}
