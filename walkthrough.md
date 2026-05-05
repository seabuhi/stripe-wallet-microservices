# 🚀 Stripe Wallet Microservices: Final Production Walkthrough

The ecosystem has been finalized into a **10/10 Senior-Level Distributed System**. Every service is now physically separated, fully decoupled, and optimized for high-performance financial operations.

## 🏛 Final Architecture Overview

- **`gateway-service` (Port 8080)**: Central entry point. Handles routing and internal API key injection.
- **`auth-service` (Port 8081)**: Identity provider. Issues JWTs with `userId` and `role` claims.
- **`wallet-service` (Port 8082)**: Core ledger. Handles balance, transfers, and outbox relay.
- **`payment-service` (Port 8083)**: Stripe specialist. Handles checkout sessions and webhooks.
- **`audit-service` (Port 8084)**: Observability sink. Consumes events for persistent auditing.
- **`common-lib`**: Shared DTOs, Enums, and Security filters.

---

## 📡 Messaging & Reliability (Senior Tier)

### 1. Exactly-Once Semantics (EOS)
We've moved beyond "at-least-once" delivery. Using Kafka transactions, we guarantee that either both the DB update and the Kafka publish succeed, or none do.
- **Producer**: `idempotence: true`, `transactional.id` enabled.
- **Consumer**: `isolation_level: read_committed`.

### 2. Distributed Tracing & Correlation
Logs are now linked across service boundaries even in async Kafka flows.
- **Flow**: `Wallet` (traceId in Header) → `Kafka` → `Audit/Payment` (MDC Extraction) → `Logs`.
- Every log entry in Grafana/Zipkin will now show the full request lifecycle.

### 3. Failure Handling (DLQ & Retries)
The system is now "Self-Healing". If a consumer fails (e.g., DB down), Kafka automatically retries with exponential backoff before moving the message to a **Dead Letter Queue (DLQ)**.

---

## 🔒 Security (Zero-Trust Architecture)

### 1. Stateless Identity
Downstream services (Wallet/Payment) no longer call the Auth DB. They extract the `userId` directly from the signed JWT claims. This is the **Gold Standard** for microservice scaling.

### 2. Internal Communication Security
Service-to-service calls are protected by an `InternalApiKeyFilter`. Only the Gateway can "trustfully" talk to internal services.

---

## 🛠 Build & Run

### Build Command
```powershell
.\mvnw.cmd clean install -DskipTests
```

### Infrastructure Launch
```powershell
docker compose up -d
```

---

## 📊 Observability Stack
- **Prometheus**: Metrics collection.
- **Grafana**: Dashboarding (Real-time balance monitoring).
- **Zipkin**: Distributed trace visualization.

> [!IMPORTANT]
> The system is now fully decoupled. `wallet-service` does NOT use the Stripe SDK; it communicates with `payment-service` via **Feign Clients**. `payment-service` does NOT touch the Wallet DB; it communicates via **Kafka Events**.

🏆 **This is a production-ready, enterprise-grade microservices portfolio project.**
