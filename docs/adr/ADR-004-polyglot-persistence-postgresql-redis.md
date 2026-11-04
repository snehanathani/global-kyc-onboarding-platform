# ADR-004: Dual-Tier Idempotency: PostgreSQL Unique Constraints and Redis L1 Cache

## Status
**ACCEPTED**

## Context
In financial onboarding APIs, clients (mobile apps, web apps, banking partners) routinely retry failed or timed-out network requests.
Using `SETNX` with a 24-hour Time-To-Live (TTL) in Redis alone has key vulnerability modes:
1. **Cache Eviction / Restarts**: If Redis restarts or drops keys under memory pressure, duplicate requests can slip through and cause double onboarding or duplicated debit card issuance.
2. **Day-Two Delivery**: A message queue redelivery or offline mobile device retrying an onboarding request on day 2 will see an expired TTL in Redis and execute duplicate operations.

Relying exclusively on relational database row writes for every incoming duplicate check introduces unnecessary I/O lock contention during burst traffic.

## Decision
We implement a **Dual-Tier Idempotency Architecture**:

1. **L1 Fast In-Memory Cache (Redis 7)**:
   - Stores `idempotency:{key}` with atomic `SETNX` and a 24-hour TTL.
   - Rejects the vast majority of immediate concurrent client retries in $< 1\text{ms}$ without touching PostgreSQL disk I/O.
2. **L2 Durable System of Record (PostgreSQL 16)**:
   - Persists records into the `idempotency_records` table with a strict `UNIQUE CONSTRAINT (idempotencyKey)`.
   - Guaranteed durable across cache restarts, node failures, and arbitrary time horizons (weeks or months).
   - If Redis misses or has evicted the key, PostgreSQL's unique constraint intercepts the duplicate and prevents double execution.

## Flow
```
Client Request -> Check Redis L1
    |-- Key exists -> Reject immediate duplicate (< 1ms)
    |-- Key absent -> Check & Insert PostgreSQL L2 (idempotency_records)
            |-- Unique constraint violation -> Reject duplicate
            |-- Insert success -> Populate Redis L1 & proceed with verification
```

## Consequences
### Positive:
- **Zero Duplicate Risk**: Complete durability across Redis failovers, evictions, or multi-day retries.
- **Low Latency**: High-frequency retries during network flapping are absorbed by Redis in $< 1\text{ms}$.

### Negative / Trade-offs:
- Adds a write to the `idempotency_records` table per distinct customer onboarding lifecycle.
