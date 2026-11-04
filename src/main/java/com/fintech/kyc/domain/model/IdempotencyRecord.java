package com.fintech.kyc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
    @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotencyKey")
})
public class IdempotencyRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String status; // "PROCESSING", "COMPLETED", "FAILED"

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String status) {
        this.id = UUID.randomUUID().toString();
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
