# ADR-002: Config-Driven Dynamic Rule DAG vs Hardcoded State Machines for Multi-Jurisdiction KYC

## Status
**ACCEPTED**

## Context
The platform moves money across 160+ countries and 40+ currencies. Regulatory mandates are in continuous flux:
- **UK FCA (MLR 2017)**: Requires ID + PEP/Sanctions screening; transfers exceeding Â£10,000 require Enhanced Due Diligence (Proof of Address / Proof of Wealth).
- **EU AMLD6**: Mandates biometric facial liveness matching, document cryptographic validation (NFC), and cross-border PEP checks.
- **US FinCEN**: Mandates CIP (Customer Identification Program) with SSN/TIN matching and OFAC list screening.
- **Singapore MAS**: Requires SingPass API integration or verified utility bill corroboration.

Hardcoding compliance branching using nested `if/else` or monolithic state machine switches in service classes causes:
- High deployment risk whenever compliance policies change.
- Inability to execute "Shadow Mode" canary testing for prospective regulatory updates.
- Long release cycles that breach tight regulatory implementation deadlines.

## Decision
We implement a **Config-Driven Directed Acyclic Graph (DAG) Rule Engine**:
1. Compliance rules are isolated into decoupled components (`KycRule`) with declarative priorities, jurisdictional filters, and evaluation logic.
2. The `DynamicRuleEngine` inspects customer profile attributes (jurisdiction, customer type, projected annual volume) and computes the minimal set of required verification checks dynamically.
3. Supports **Shadow Mode**: New or amended rules can run silently alongside production rules, calculating theoretical pass/fail rates and risk score impacts without blocking live customer conversions.
4. Allows Product Managers and Compliance Squad leads to adjust verification thresholds with rapid validation.

## Consequences
### Positive:
- **Zero-Downtime Regulatory Agility**: New corridor rules can be enabled and tested in shadow mode before general rollout.
- **High Testability**: Each jurisdiction rule is an isolated unit with dedicated deterministic unit tests.
- **Audit Transparency**: Regulatory compliance rationales are generated deterministically and written directly into the `compliance_audit_logs` table.

### Negative / Trade-offs:
- Slightly higher cognitive overhead than naive linear procedural code.
- Rule priority conflicts must be managed carefully using strict ordering guarantees.
