# 💳# Stripe Wallet API — Production-Grade Microservices Architecture

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.2-brightgreen?style=flat-square&logo=springboot)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023-blue?style=flat-square&logo=spring)
![Kafka](https://img.shields.io/badge/Kafka-Distributed-black?style=flat-square&logo=apachekafka)

> An enterprise-grade distributed fintech ecosystem featuring API Gateway, Event-Driven communication (Kafka), Transactional Outbox, separate databases per service, and a full observability stack.

---

## ✨ Features

- **API Gateway Orchestration**: Centralized routing via Spring Cloud Gateway.
- **Database per Service**: Isolated persistence (`auth_db`, `wallet_db`, `audit_db`) to prevent tight coupling.
- **Reliable Messaging (Kafka + Outbox)**: Atomic DB updates + synchronous relay with Retry & DLQ (Dead Letter Queue).
- **Distributed Observability**: TraceId propagation across HTTP and Kafka via Zipkin + Prometheus + Grafana.
- **Enterprise Consistency**: `Isolation.SERIALIZABLE` + Pessimistic Locking + Invariant Checks.

---

## 🏗 Architecture

- **Ecosystem Modules**:
  - `gateway-service` (8080): The entry point for all client traffic.
  - `auth-service` (8081): Identity provider with its own `auth_db`.
  - `wallet-service` (8082): Core financial engine with `wallet_db` (Producer).
  - `payment-service` (8083): Stripe checkout & webhook handler.
  - `audit-service` (8084): Background consumer with `audit_db`, Retry & DLQ.
  - `common-lib`: Clean shared library (DTOs, Events, Constants).

---

## 🚀 Running the Ecosystem

### 1. Build Entire System
```bash
mvn clean install
```

### 2. Launch Everything (Infrastructure + Apps)
```bash
docker compose up -d
```

### 3. Service Entry Points
- **API Gateway**: `http://localhost:8080/api/v1/...`
- **Tracing**: `http://localhost:9411` (Zipkin)
- **Metrics**: `http://localhost:3000` (Grafana)

---

## 📡 API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/register` | ❌ | Create account + Wallet |
| `POST` | `/api/v1/auth/login` | ❌ | Get JWT Token |
| `GET` | `/api/v1/wallet/balance` | ✅ JWT | Current balance |
| `POST` | `/api/v1/wallet/top-up` | ✅ JWT | Start top-up (Idempotent) |
| `POST` | `/api/v1/wallet/transfer` | ✅ JWT | User-to-user (Idempotent) |
| `GET` | `/api/v1/wallet/transactions` | ✅ JWT | History (Paginated + Filter) |
| `GET` | `/api/v1/wallet/transactions/export` | ✅ JWT | Download CSV history |
| `GET` | `/api/v1/wallet/admin/transactions` | ✅ ADMIN | Global view (Admin only) |
| `POST` | `/api/v1/stripe/webhook` | 🛡 Sig | Webhook handler |

---

## 🛡 Security Design

1. **Pessimistic Locking**: Prevents double-spending during concurrent transfers/top-ups.
2. **Amount Validation**: Webhook compares Stripe payment amount against our DB to detect manipulation.
3. **Webhook Verification**: HMAC-SHA256 signature check on every Stripe event.
4. **Idempotency**: Scoped `(key, user, endpoint)` uniqueness prevents duplicate processing.

---

## 📄 License
MIT
