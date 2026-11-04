# Global KYC & Onboarding Decision Platform: Architectural Specification

## 1. Problem Domain & Scale Framing

The **Global KYC (Know Your Customer) and Onboarding Platform** is the reference decision engine modeled on international money movement architectures across multiple regulatory regimes (UK FCA, EU AMLD6, US FinCEN, Singapore MAS).

### Scale & Real-World Framing
A global fintech onboarding approximately 500,000 customers per month handles an average steady-state throughput of **~0.2 to 2 transactions per second (TPS)**, with peak marketing surges reaching 30–50 TPS.

Therefore, the primary architectural challenge in Global KYC is **not raw high-throughput streaming**. Rather, the mission-critical engineering challenges center on:
1. **Regulatory Correctness & Determinism**: Verifying customers against shifting legal standards without regressions.
2. **Auditability & Evidentiary Proof**: Maintaining an immutable, tamper-evident chronological log of every rule evaluated, vendor confidence score, and officer decision for FCA/BaFin audits.
3. **Safe Policy Evolution**: Introducing new compliance checks (e.g. enhanced due diligence thresholds) without breaking live signups, verified via **Shadow Mode** before enforcement.
4. **Third-Party Fault Isolation**: Decoupling fragile external SaaS vendors (IDV, biometrics, PEP/sanctions) through circuit breakers and active failovers.
5. **Data Protection & PII Privacy**: Enforcing strict zero-knowledge principles, field-level encryption, and log redaction.

---

## 2. High-Level Architecture Diagram

```mermaid
flowchart TD
    subgraph ClientLayer["Client & Partner Ingress"]
        Web["Web and Portal Applications"]
        Mobile["iOS and Android Native Apps"]
        B2B["Embedded Banking Partner APIs"]
    end

    Gateway["API Gateway and Rate Limiter"]
    ClientLayer --> Gateway

    subgraph ServiceMesh["Global KYC Service Mesh"]
        Orchestrator["KycVerificationOrchestrator<br/>(Spring Boot 3 / Java 21)"]
        RuleEngine["DynamicRuleEngine<br/>(Jurisdiction DAG and Shadow Evaluator)"]
        ResilienceGw["IdentityVerificationGateway<br/>(Resilience4j Circuit Breaker)"]
        OutboxWorker["OutboxPublisher<br/>(Scheduled CDC / At-Least-Once Delivery)"]
        AgentTooling["HumanReviewService<br/>(Compliance Officer Review API)"]
    end

    Gateway --> Orchestrator
    Gateway --> AgentTooling

    Orchestrator --> RuleEngine
    Orchestrator --> ResilienceGw

    subgraph DataPersistence["Polyglot Persistence Layer"]
        Postgres[("PostgreSQL 16 Primary<br/>ACID Entities, Audit Logs, Outbox, Durable Idempotency")]
        RedisCache[("Redis 7 Cluster<br/>L1 Idempotency Tokens and Rate Limits")]
    end

    Orchestrator --> Postgres
    Orchestrator --> RedisCache
    OutboxWorker --> Postgres

    subgraph ExternalVendors["External Verification Providers (Stubbed)"]
        PrimaryVendor["Primary Vendor Onfido<br/>NFC ID and Biometrics"]
        FallbackVendor["Fallback Vendor Veriff<br/>Active Circuit Failover"]
        SanctionsVendor["Sanctions Screening ComplyAdvantage<br/>OFAC and HMT Watchlists"]
    end

    ResilienceGw -->|"Circuit Closed: Primary"| PrimaryVendor
    ResilienceGw -.->|"Circuit Open: Failover"| FallbackVendor
    Orchestrator --> SanctionsVendor

    subgraph StreamingEcosystem["Event Streaming and Downstream Mesh"]
        KafkaBus[["Apache Kafka Broker Cluster<br/>(KRaft Mode)"]]
        Downstream1["Account Activation Service"]
        Downstream2["Debit Card Issuance Service"]
        Downstream3["Compliance Audit Reporting"]
    end

    OutboxWorker -->|"Publish Events"| KafkaBus
    KafkaBus --> Downstream1
    KafkaBus --> Downstream2
    KafkaBus --> Downstream3
```

---

## 3. End-to-End Straight-Through Processing (STP) Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Customer as User / Client App
    participant API as KycVerificationController
    participant Orch as KycVerificationOrchestrator
    participant Rules as DynamicRuleEngine
    participant Res as IdentityVerificationGateway
    participant Vendor as Primary Vendor Onfido
    participant DB as PostgreSQL ACID Outbox
    participant Kafka as Apache Kafka

    Customer->>API: POST /api/v1/kyc/verifications (Idempotency Key)
    API->>Orch: initiateAndProcessVerification(request)
    Orch->>DB: Check L1/L2 Idempotency Token and Persist INITIATED Case
    Orch->>Rules: evaluateCase(case, existingChecks)
    Rules-->>Orch: Required Checks: ID_DOC, BIOMETRICS, SANCTIONS
    
    par Identity and Biometrics Check
        Orch->>Res: executeDocumentVerification(customerId, docId)
        Res->>Vendor: POST /v3/checks (Timeout: 1500ms)
        Vendor-->>Res: 200 OK (Confidence: 0.97)
        Res-->>Orch: CheckStatus.PASSED
    and Sanctions and PEP Check
        Orch->>Vendor: screenSanctionsAndPep(legalName, country)
        Vendor-->>Orch: Clean (Score: 0.0, CheckStatus.PASSED)
    end

    Note over Orch: All checks passed with confidence >= 0.85. Straight-Through-Processing Approved
    
    rect rgb(240, 255, 240)
        Orch->>DB: ATOMIC TX BEGIN: Update Case to AUTO_APPROVED, Insert AuditLog, Insert OutboxMessage, COMMIT
    end

    Orch-->>API: 201 Created (VerificationCaseResponse)
    API-->>Customer: HTTP 201 AUTO_APPROVED (under 450ms)

    Note over DB,Kafka: Asynchronous Outbox Dispatch
    DB->>Kafka: OutboxPublisher polls and publishes to kyc.verification.completed
```

---

## 4. Resilience4j Circuit Breaker & Failover Policy

### Circuit Breaker State Transitions
```mermaid
stateDiagram-v2
    [*] --> Closed: Initial State

    state Closed {
        [*] --> HealthyRequests
        HealthyRequests --> HealthyRequests: Calls succeed
    }

    Closed --> Open: Failure Rate >= 50% over 10 calls
    
    state Open {
        [*] --> FastFail
        FastFail --> FallbackToSecondary: Immediately route calls to Veriff
    }

    Open --> HalfOpen: Wait Duration 8000ms elapsed
    
    state HalfOpen {
        [*] --> ProbeRequests: Allow 3 trial requests
        ProbeRequests --> ProbeSuccess: Majority succeed
        ProbeRequests --> ProbeFail: Majority fail
    }

    HalfOpen --> Closed: ProbeSuccess - Primary recovered
    HalfOpen --> Open: ProbeFail - Remain on Veriff fallback
```

### The Sanctions Invariant: Fail-Closed
A critical architectural distinction exists between document verification and sanctions screening:
* **Identity Documents (Fail-Over Allowed)**: If Onfido fails, traffic safely fails over to Veriff or queues asynchronously.
* **Sanctions & PEP (Fail-Closed Strictly)**: Sanctions screening **must never fail open**. If the sanctions provider times out, throws a 5xx, or experiences a network partition, the verification check is unconditionally flagged, straight-through approval is withheld, and the case is quarantined for manual review.

---

## 5. Data Protection, PII & Regulatory Auditing

1. **Field-Level Encryption**: Sensitive identifiers (passport numbers, national tax IDs, SSNs) are encrypted at rest using AES-256 before database insertion.
2. **Log Redaction**: Standard log appenders sanitize PII to prevent customer credentials or biometric identifiers from leaking into ELK/Splunk logs.
3. **Immutable Audit Trails**: The `compliance_audit_logs` table records every actor, rule evaluation rationale, and status transition with UTC timestamps for FCA/BaFin regulatory reporting.
4. **Dual-Tier Idempotency**: Redis L1 cache absorbs high-frequency network retries ($< 1\text{ms}$), backed by a durable PostgreSQL unique constraint (`uk_idempotency_key`) to guarantee consistency across cache evictions and node restarts.

---

## 6. Zero-Downtime Database Migrations (Expand/Contract Pattern)

Schema updates follow the **Expand / Contract (Parallel Run)** pattern:
1. **Expand Phase**: Add new nullable columns or tables. Application continues running uninterrupted.
2. **Dual-Write Phase**: Deploy application version writing to both old and new columns.
3. **Backfill Phase**: Asynchronous batch job backfills historical data.
4. **Contract Phase**: Read path switched to new column; legacy column deprecated and dropped in a subsequent release.
