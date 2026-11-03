# ADR-004: Polyglot Persistence: PostgreSQL for Relational ACID Integrity and Redis for Distributed Idempotency

## Status
**ACCEPTED**

## Context
A mission-critical Global KYC engine handles contrasting data access patterns:
1. **Strong Consistency & Auditability**:
   - Customer case states, verification check outcomes, and regulatory audit trails require strict ACID guarantees, foreign key integrity, row-level optimistic locking (`@Version`), and schema enforcement for regulatory reporting (FCA, BaFin).
2. **High-Throughput Distributed State**:
   - API idempotency keys, distributed rate-limiting counters, and session deduplication windows require sub-millisecond latency and automatic Time-To-Live (TTL) expiration.
   - Hitting a relational database for duplicate request tokens on mobile app retries creates unnecessary I/O lock contention.

## Decision
We adopt a **Polyglot Persistence Architecture**:
1. **Primary Relational Store (PostgreSQL 16)**:
   - Stores `verification_cases`, `verification_checks`, `compliance_audit_logs`, and `outbox_messages`.
   - Utilizes optimistic locking (`version` column) to prevent concurrent race conditions during compliance officer claims.
   - Enforces indexed query paths on `customerId`, `jurisdiction`, and `status`.
2. **Distributed In-Memory Cache (Redis 7)**:
   - Stores idempotency tokens (`idempotency:{key}`) with atomic `SETNX` and a 24-hour TTL.
   - Provides distributed lock capabilities for multi-instance deployments.
   - Implements resilient local fallback caching in the event of Redis node failover.

## Consequences
### Positive:
- **Optimal Latency**: Idempotency token checks execute in < 1ms in Redis, shielding PostgreSQL from retry storms.
- **Regulatory Compliance**: Complete, immutable historical audit logs maintained reliably in PostgreSQL.
- **Optimistic Concurrency**: Prevents two compliance agents from conflicting while claiming the same case.

### Negative / Trade-offs:
- Requires managing two storage layers in production operations.
- Redis must be configured with replication (Sentinel / Redis Cluster) to maintain high availability.
