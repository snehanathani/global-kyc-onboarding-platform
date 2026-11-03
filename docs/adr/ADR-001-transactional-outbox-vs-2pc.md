# ADR-001: Transactional Outbox Pattern vs Two-Phase Commit (2PC) for Distributed Consistency

## Status
**ACCEPTED**

## Context
The platform operates as an asynchronous, event-driven distributed system comprising 1,000+ microservices. In the Global KYC and Onboarding domain, customer verification state transitions (`AUTO_APPROVED`, `MANUAL_REVIEW_REQUIRED`, `REJECTED`) must trigger critical downstream actions:
- Unlocking multi-currency account balances and payment rails.
- Issuing debit cards via card-factory partners.
- Emitting compliance reporting events for financial regulators (UK FCA, BaFin, FinCEN).

Writing to the local relational database (`verification_cases`) and publishing an event to Apache Kafka creates the classic **Dual-Write Problem**:
1. If the database commit succeeds but Kafka dispatch fails (e.g. network partition, broker timeout), the event is lost forever, leaving the customer in an inconsistent, orphaned state.
2. If Kafka is published first and the DB transaction fails, downstream services trigger side-effects on non-existent accounts (phantom activation).
3. Two-Phase Commit (2PC / XA transactions) across heterogeneous systems (PostgreSQL + Kafka) is notoriously fragile, introduces high coordination latency, and creates single points of failure that violate platform high-throughput requirements.

## Decision
We implement the **Transactional Outbox Pattern** using PostgreSQL and Apache Kafka:
1. Every state mutation of `VerificationCase` atomically writes an `OutboxMessage` record to the PostgreSQL `outbox_messages` table within the **same local ACID transaction** (`@Transactional`).
2. An asynchronous `OutboxPublisher` processes pending records and pushes them to designated Kafka topics (`kyc.verification.completed`, `kyc.agent.review_required`).
3. Events are published with **At-Least-Once Delivery** semantics and partition keys aligned with `customerId`/`caseId` to preserve ordering.
4. Consumers implement **Idempotency Keys** backed by Redis and unique database constraints to achieve end-to-end effective exactly-once semantics.

## Consequences
### Positive:
- **Zero Data Loss**: Eliminates dual-write inconsistencies completely.
- **Decoupled Availability**: PostgreSQL writes succeed even if Kafka brokers are undergoing maintenance or experiencing rolling restarts.
- **Traceability**: Outbox table acts as an immutable chronological queue of all emitted business events.

### Negative / Trade-offs:
- Requires a polling or Change-Data-Capture (CDC/Debezium) mechanism to push outbox records to Kafka.
- Downstream consumers must be strictly idempotent to handle potential duplicate event deliveries.
