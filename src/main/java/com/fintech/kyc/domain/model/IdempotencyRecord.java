package com.fintech.kyc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
    @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotencyKey")
})
public class IdempotencyRecord {

    public enum Status {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String requestHash) {
        this.id = UUID.randomUUID().toString();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = Status.IN_PROGRESS;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
