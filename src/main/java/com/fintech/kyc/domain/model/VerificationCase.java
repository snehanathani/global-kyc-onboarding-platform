package com.fintech.kyc.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "verification_cases", indexes = {
    @Index(name = "idx_case_customer", columnList = "customerId"),
    @Index(name = "idx_case_status", columnList = "status"),
    @Index(name = "idx_case_jurisdiction", columnList = "jurisdiction")
})
public class VerificationCase {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerType customerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Jurisdiction jurisdiction;

    @Column(nullable = false)
    private String legalName;

    private String registrationOrTaxId;

    private BigDecimal expectedAnnualVolumeGbp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    private Double aggregateRiskScore;

    private String assignedAgentId;

    @Column(length = 2000)
    private String rejectionReason;

    private Boolean straightThroughProcessed;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "verificationCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<VerificationCheck> checks = new ArrayList<>();

    public VerificationCase() {
    }

    public VerificationCase(String customerId, CustomerType customerType, Jurisdiction jurisdiction,
                            String legalName, String registrationOrTaxId, BigDecimal expectedAnnualVolumeGbp) {
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.customerType = customerType;
        this.jurisdiction = jurisdiction;
        this.legalName = legalName;
        this.registrationOrTaxId = registrationOrTaxId;
        this.expectedAnnualVolumeGbp = expectedAnnualVolumeGbp != null ? expectedAnnualVolumeGbp : BigDecimal.ZERO;
        this.status = VerificationStatus.PENDING;
        this.straightThroughProcessed = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addCheck(VerificationCheck check) {
        checks.add(check);
        check.setVerificationCase(this);
    }

    public void updateStatus(VerificationStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public CustomerType getCustomerType() { return customerType; }
    public void setCustomerType(CustomerType customerType) { this.customerType = customerType; }

    public Jurisdiction getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(Jurisdiction jurisdiction) { this.jurisdiction = jurisdiction; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getRegistrationOrTaxId() { return registrationOrTaxId; }
    public void setRegistrationOrTaxId(String registrationOrTaxId) { this.registrationOrTaxId = registrationOrTaxId; }

    public BigDecimal getExpectedAnnualVolumeGbp() { return expectedAnnualVolumeGbp; }
    public void setExpectedAnnualVolumeGbp(BigDecimal expectedAnnualVolumeGbp) { this.expectedAnnualVolumeGbp = expectedAnnualVolumeGbp; }

    public VerificationStatus getStatus() { return status; }
    public void setStatus(VerificationStatus status) { this.status = status; }

    public Double getAggregateRiskScore() { return aggregateRiskScore; }
    public void setAggregateRiskScore(Double aggregateRiskScore) { this.aggregateRiskScore = aggregateRiskScore; }

    public String getAssignedAgentId() { return assignedAgentId; }
    public void setAssignedAgentId(String assignedAgentId) { this.assignedAgentId = assignedAgentId; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Boolean getStraightThroughProcessed() { return straightThroughProcessed; }
    public void setStraightThroughProcessed(Boolean straightThroughProcessed) { this.straightThroughProcessed = straightThroughProcessed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<VerificationCheck> getChecks() { return checks; }
    public void setChecks(List<VerificationCheck> checks) { this.checks = checks; }
}
