# Global KYC & Onboarding Decision Platform 🚀

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-2.2.0-blue.svg)](https://resilience4j.readme.io/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.7-black.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Architect-level, mission-critical Global KYC and Onboarding Decision Engine modeled on leading tier-1 cross-border multi-currency fintech architectures.** Powers personal onboarding, corporate KYB verification, and embedded banking partner rails across **160+ countries and 40+ currencies**.

---

## 🏛️ Executive Summary

Processing hundreds of billions in cross-border volume annually while onboarding ~500k customers per month requires an onboarding engine that achieves high velocity without compromising on regulatory compliance.

This platform implements the architecture required for **Global KYC and Onboarding Systems**:
1. **Straight-Through Processing (STP)**: $> 85\%$ of verified customers auto-approved in $< 500\text{ms}$ with zero human intervention.
2. **Multi-Tier Fault Tolerance (Resilience4j)**: Active circuit breaker failover across identity providers (Onfido to Veriff) and graceful degradation when external SaaS dependencies experience outages.
3. **Zero Data Loss (Transactional Outbox Pattern)**: Atomic PostgreSQL transaction writes paired with asynchronous Kafka event streaming to eliminate dual-write inconsistencies.
4. **Config-Driven Compliance DAG**: Declarative multi-jurisdiction rule graph (UK FCA, EU AMLD6, US FinCEN, SG MAS) with **Shadow Mode** canary deployments for zero-downtime policy updates.
5. **Human-in-the-Loop Operational Tooling**: High-risk actors and sanctions matches quarantined into real-time triage queues for Compliance Officer review.

---

## 📐 System Architecture

```mermaid
flowchart TD
    subgraph Ingress["Client and Ingress Layer"]
        Web["Web and Portal Applications"]
        Mobile["iOS and Android Native Apps"]
        Platform["B2B Banking APIs"]
    end

    Web --> Gateway["API Gateway and Rate Limiter"]
    Mobile --> Gateway
    Platform --> Gateway

    subgraph CoreEngine["Global KYC Service Mesh"]
        Orchestrator["KycVerificationOrchestrator<br/>(Java 21 / Spring Boot 3)"]
        RuleDAG["DynamicRuleEngine<br/>(Jurisdiction DAG and Shadow Mode)"]
        ResilienceGw["IdentityVerificationGateway<br/>(Resilience4j Circuit Breakers)"]
        OutboxWorker["OutboxPublisher<br/>(Transactional Outbox / CDC)"]
        ReviewOps["HumanReviewService<br/>(Compliance Review Queue API)"]
    end

    Gateway --> Orchestrator
    Gateway --> ReviewOps

    Orchestrator --> RuleDAG
    Orchestrator --> ResilienceGw

    subgraph DataStorage["Polyglot Persistence Layer"]
        Postgres[("PostgreSQL 16 Primary<br/>ACID Entities, Audit Logs and Outbox")]
        RedisCache[("Redis 7<br/>Idempotency Tokens and Locks")]
    end

    Orchestrator --> Postgres
    Orchestrator --> RedisCache
    OutboxWorker --> Postgres

    subgraph ExternalVendors["External Verification Providers"]
        PrimaryVendor["Primary Vendor Onfido<br/>NFC ID and Biometrics"]
        FallbackVendor["Fallback Vendor Veriff<br/>Active Circuit Failover"]
        SanctionsVendor["Sanctions ComplyAdvantage<br/>OFAC and HMT Screening"]
    end

    ResilienceGw -->|"Circuit Closed: Normal Route"| PrimaryVendor
    ResilienceGw -.->|"Failover: Circuit Open"| FallbackVendor
    Orchestrator --> SanctionsVendor

    subgraph EventStream["Apache Kafka Streaming"]
        KafkaBus[["Apache Kafka Broker Cluster<br/>(KRaft Mode)"]]
        Downstream1["Account Activation Service"]
        Downstream2["Debit Card Issuance Service"]
        Downstream3["Regulatory Compliance Reporting"]
    end

    OutboxWorker -->|"Publish Events"| KafkaBus
    KafkaBus --> Downstream1
    KafkaBus --> Downstream2
    KafkaBus --> Downstream3
```

---

## 🛠️ Tech Stack & Key Design Patterns

| Layer | Technology | Key Pattern / Responsibility |
| :--- | :--- | :--- |
| **Runtime & Framework** | Java 21 (LTS), Spring Boot 3.3.4 | Modern JVM with ZGC garbage collector, virtual threads ready |
| **Resilience & Failover** | Resilience4j 2.2.0 | `@CircuitBreaker`, `@Bulkhead`, `@Retry`, multi-tier vendor failover |
| **Event Streaming** | Apache Kafka 3.7 (KRaft) | Event-driven microservices, partition-keyed ordered events |
| **Relational Database** | PostgreSQL 16 | ACID transactional guarantees, row-level optimistic locking (`@Version`) |
| **In-Memory Store** | Redis 7 | Distributed idempotency locks (`SETNX`), 24h TTL deduplication |
| **Reliable Messaging** | Transactional Outbox Pattern | Polling publisher avoiding dual-write discrepancies between DB & Kafka |
| **Observability** | Micrometer, Prometheus, Actuator | Real-time STP conversion metrics, p99 latency timers, breaker status |

---

## 🚀 Quickstart & Local Execution

### Prerequisites
- **Java 21** (`java -version`)
- **Maven 3.9+** (Included via `./mvnw`)
- **Docker & Docker Compose** (Optional for full multi-container stack)

### 1. Run Unit & Integration Tests (100% Automated)
```powershell
./mvnw clean test
```
*Executes all 11 unit and integration tests including simulated vendor outages, Resilience4j circuit breaker failovers, Straight-Through-Processing, and idempotent concurrency protection.*

### 2. Run the Application
```powershell
./mvnw spring-boot:run
```
The application will boot at `http://localhost:8080`.

### 3. Run the Full Production Topology via Docker Compose
```powershell
docker-compose up --build
```
*Spins up the Spring Boot App, PostgreSQL 16, Redis 7, Apache Kafka (KRaft mode), and Prometheus.*

---

## 📡 Live API Walkthrough & Verification Scenarios

### 1. Straight-Through Processing (STP) Auto-Approval (< 500ms)
Verify an individual customer in the UK corridor:
```bash
curl -X POST http://localhost:8080/api/v1/kyc/verifications \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-uk-101",
    "customerType": "INDIVIDUAL",
    "jurisdiction": "GB",
    "legalName": "Arthur Conan Doyle",
    "expectedAnnualVolumeGbp": 1500.00,
    "documentType": "PASSPORT",
    "documentId": "PASS-UK-888999",
    "idempotencyKey": "idem-key-stp-001"
  }'
```
**Response (Instant 201 Created):**
```json
{
  "id": "29f44640-f212-4c63-a7a3-8af7d90edf7a",
  "customerId": "cust-uk-101",
  "customerType": "INDIVIDUAL",
  "jurisdiction": "GB",
  "legalName": "Arthur Conan Doyle",
  "status": "AUTO_APPROVED",
  "aggregateRiskScore": 0.0,
  "straightThroughProcessed": true,
  "checks": [
    {
      "checkType": "IDENTITY_DOCUMENT",
      "status": "PASSED",
      "vendorName": "ONFIDO_PRIMARY",
      "confidenceScore": 0.97
    },
    {
      "checkType": "SANCTIONS_AND_PEP",
      "status": "PASSED",
      "vendorName": "COMPLY_ADVANTAGE_SANCTIONS",
      "confidenceScore": 0.99
    }
  ]
}
```

---

### 2. Simulating a 3rd-Party Vendor Outage & Testing Resilience4j Failover
Simulate an unexpected outage on the primary identity vendor (Onfido):
```bash
curl -X POST "http://localhost:8080/api/v1/admin/vendors/primary/outage?simulate=true"
```
Now submit another onboarding verification:
```bash
curl -X POST http://localhost:8080/api/v1/kyc/verifications \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-resilience-202",
    "customerType": "INDIVIDUAL",
    "jurisdiction": "GB",
    "legalName": "John Watson",
    "expectedAnnualVolumeGbp": 3000.00
  }'
```
**Outcome:**
The request succeeds seamlessly! The `IdentityVerificationGateway` circuit breaker intercepts the downstream outage and dynamically routes to `VERIFF_SECONDARY_FALLBACK`:
```json
{
  "checkType": "IDENTITY_DOCUMENT",
  "status": "FALLBACK_VENDOR_USED",
  "vendorName": "VERIFF_SECONDARY_FALLBACK",
  "confidenceScore": 0.93,
  "details": "Fallback automated check passed via secondary provider Veriff."
}
```

---

### 3. Sanctions Hit & Compliance Officer Review Queue
Submit an onboarding request for an individual matching a sanctions watchlist:
```bash
curl -X POST http://localhost:8080/api/v1/kyc/verifications \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-bad-303",
    "customerType": "INDIVIDUAL",
    "jurisdiction": "GB",
    "legalName": "SANCTIONED INDIVIDUAL TEST",
    "expectedAnnualVolumeGbp": 2500.00
  }'
```
**Outcome:**
Status is immediately set to `MANUAL_REVIEW_REQUIRED`. Straight-through processing is withheld, and the case is queued for human review.

#### Fetch Pending Review Queue:
```bash
curl http://localhost:8080/api/v1/compliance/queue
```

#### Claim Case as Compliance Officer:
```bash
curl -X POST "http://localhost:8080/api/v1/compliance/cases/{caseId}/claim?agentId=agent_sarah_london"
```

#### Submit Audited Compliance Decision:
```bash
curl -X POST http://localhost:8080/api/v1/compliance/cases/{caseId}/decision \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent_sarah_london",
    "action": "APPROVE",
    "justificationReason": "False positive: customer date of birth and passport nationality distinguishes them from designated sanctions subject."
  }'
```

---

## 📚 Deep-Dive Documentation & Interview Guides

- **[ARCHITECTURE.md](ARCHITECTURE.md)**: Exhaustive architectural blueprint, PACELC / CAP trade-offs, thread-pool sizing, zero-downtime DB migrations, and 100M+ customer scale.
- **[INTERVIEW_PLAYBOOK.md](INTERVIEW_PLAYBOOK.md)**: The definitive Senior II / Lead Software Engineer interview preparation guide:
  - Cross-functional squad autonomy ("You build it, you run it").
  - Whiteboard System Design walkthrough for Global KYC.
  - 25+ Real fintech interview questions with Lead-level exemplar answers.
  - Collaboration with Product Managers: Translating FCA/AMLD6 regulations into technical milestones, measuring conversion vs compliance risk, running canary rule rollouts.
- **[Architectural Decision Records (ADRs)](docs/adr/)**:
  - [ADR-001: Transactional Outbox vs Two-Phase Commit](docs/adr/ADR-001-transactional-outbox-vs-2pc.md)
  - [ADR-002: Dynamic Rule DAG vs Hardcoded State Machines](docs/adr/ADR-002-dynamic-rule-dag-vs-hardcoded-fsm.md)
  - [ADR-003: Multi-tier Resilience & Vendor Failover Strategy with Resilience4j](docs/adr/ADR-003-resilience4j-circuit-breaker-multi-tier-failover.md)
  - [ADR-004: Polyglot Persistence: PostgreSQL & Redis](docs/adr/ADR-004-polyglot-persistence-postgresql-redis.md)
  - [ADR-005: Straight-Through Processing (STP) vs Human Triage](docs/adr/ADR-005-straight-through-processing-and-human-triage.md)

---

## 👤 Author
**Sneha Nathani**  
[GitHub Profile](https://github.com/snehanathani) | [Repository](https://github.com/snehanathani/global-kyc-onboarding-platform)
