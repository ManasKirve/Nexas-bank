# NexaBank — Architecture (Phase 1 Foundation)

> Educational, original portfolio build. Not affiliated with, and not reproducing,
> any real bank's proprietary systems.

## Purpose

Enterprise-style digital banking + fraud monitoring platform, built as a
**modular monolith** so future services can be extracted without rewrites.

## System overview (Phase 1)

```
Angular (localhost:4200)
  ↓ HTTP (HttpClient, /api/v1/*)
Spring Boot (localhost:8080)
  ↓ Spring Data JPA
PostgreSQL (localhost:5432, via Docker)
```

Future evolution (later phases):

```
Angular
  ↓
API Layer (REST controllers, DTOs, validation)
  ↓
Spring Security (authN/authZ, RBAC)
  ↓
Application Services (use cases, transactions)
  ↓
Domain / Business Logic (incl. Fraud Engine, Risk Scoring)
  ↓
Repositories (Spring Data JPA)
  ↓
PostgreSQL (Flyway/Liquibase migrations)
```

Supporting components (planned): Fraud Engine, Audit Service, Notification
Service, Scheduler, Observability (Actuator + structured logging), external
integrations (mocked).

## Backend layering

```
Controller → Service → Repository → Database
```

- `controller/` — thin REST adapters; no business logic.
- `service/` — use cases + transactional boundaries (`@Transactional` in later phases).
- `repository/` — Spring Data JPA interfaces only.
- `entity/` — JPA persistence model, never exposed over REST.
- `dto/` — API contracts (records where suitable).
- `exception/` — `GlobalExceptionHandler` + `ApiError` standard body.
- `config/` — `SecurityConfig` (permit-all in Phase 1), `WebConfig` (CORS).
- Reserved: `security/`, `audit/`, `fraud/`, `transaction/` for later phases.

Banking principles the structure is ready for:

- ACID transactions, idempotency keys, immutable transaction records
- Optimistic/pessimistic locking, consistency boundaries
- RBAC, secure auth, centralized error handling
- Audit trails, structured logging, observability
- Fraud pipeline: Transaction → Validation → Risk Evaluation → Rules →
  Score → Decision (Approve/Review/Block) → Audit Event → Notification

## Frontend architecture

Standalone components + Router + HttpClient + Reactive Forms + Signals.

- `app/core/` — app-wide services (`HealthService`), interceptors
  (`apiErrorInterceptor`), singleton concerns.
- `app/shared/` — reusable UI (`BackendStatusComponent`, `StatusBadgeComponent`),
  models, utils.
- `app/features/` — one folder per domain: `dashboard`, `authentication`,
  `accounts`, `transactions`, `transfers`, `fraud`, `customers`, `admin`.
- `app.routes.ts` — placeholder routes for all of the above.
- `environments/` — `environment.ts` (prod) + `environment.development.ts`
  (dev, swapped via `fileReplacements` in the `development` build config).
- `app.ts` is the root component (Angular 22 CLI default name; plays the role
  of the classic `app.component.ts`): sidebar + topbar + `<router-outlet/>`.

## Configuration & profiles

- Backend: `application.yml` (base) + `application-dev.yml` + `application-prod.yml`.
- Secrets via env vars: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`,
  `CORS_ALLOWED_ORIGINS`, `SPRING_PROFILES_ACTIVE`.
- Frontend base URL via `environment.apiBaseUrl` (default `http://localhost:8080`).

## Why a modular monolith first

Microservices are deferred deliberately: a well-modularized monolith gives
ACID simplicity now and clean extraction points (fraud, audit, notifications)
later.
