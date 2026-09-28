# NexaBank — Enterprise Digital Banking & Fraud Monitoring Platform

> **Educational / portfolio build.** An original enterprise-style banking
> application for learning and interview preparation. It does **not** reproduce
> any real bank's proprietary systems, APIs, or business rules.

## Running it — local demo mode

The Angular app runs **entirely in the browser against LocalStorage**. There is
no backend, no database, no Docker, and no login.

```powershell
cd nexa-bank/frontend
npm install --legacy-peer-deps   # legacy flag works around an npm 10.7 peer bug; .npmrc sets it by default
npm start                         # ng serve → http://localhost:4200
```

Open `http://localhost:4200` — it redirects to the dashboard.

On the **first launch only**, the app seeds a realistic dataset: 5 customers,
8 accounts, beneficiaries, a full transaction ledger, and a fraud queue whose
alerts are produced by the real rule engine rather than typed in. Data persists
across refreshes and browser restarts.

To wipe and re-seed at any time, open **Admin → Reset Demo Data** (asks for
confirmation first).

### What replaced authentication

The Docker build required a JWT session, and each route declared which roles
could reach it. Local demo mode has removed that layer outright rather than
simulating it:

- no login, registration, logout, token, or fake user;
- no `authGuard` / `roleGuard`, so every screen is reachable;
- no `Authorization` header, because there is no server to send it to;
- **Admin → Acting demo customer** selects whose money the "my accounts",
  "my transactions" and "beneficiaries" screens show. It is a data filter, not
  a session, and it is how the app knows whose ledger to read now that there is
  no JWT to derive ownership from.

## Data layer

All persistence goes through one gateway, `core/services/local-storage.service.ts`.
No component or feature service touches `localStorage` directly.

| File | Role |
|---|---|
| `local-storage.service.ts` | The only `localStorage` caller: typed reads/writes, key registry, availability probing, size reporting |
| `demo-repository.service.ts` | Collection-level accessors, seeding, reset, acting-customer selection, activity trail |
| `ledger.service.ts` | The money-movement pipeline shared by deposits, withdrawals and transfers |
| `fraud-rule-engine.service.ts` | Port of the backend's six fraud rules, thresholds and score bands |
| `demo-data.ts` | The deterministic seed dataset |
| `demo-api.ts` | Helpers: money rounding, references, paging, `HttpErrorResponse`-shaped errors |

The feature services (`CustomerService`, `AccountService`, `TransactionService`,
`TransferService`, `BeneficiaryService`, `FraudService`, `DashboardService`,
`HealthService`) keep the exact method signatures and `Observable` return types
they had when they called the REST API, and still fail with
`HttpErrorResponse` carrying the original business status codes (400, 404, 409,
422). That is why the components' existing error handling works unchanged.

### Storage keys

| Key | Contents |
|---|---|
| `nexabank_customers` | Customer records |
| `nexabank_accounts` | Account records with running balances |
| `nexabank_transactions` | Immutable ledger, deposits, withdrawals and both transfer legs |
| `nexabank_beneficiaries` | Payees owned by the acting customer |
| `nexabank_fraud_alerts` | Alert queue and its workflow state |
| `nexabank_fraud_evaluations` | Every engine decision, including approvals |
| `nexabank_audit_log` | Demo activity trail |
| `nexabank_active_customer_id` | The acting demo customer |
| `nexabank_demo_initialized` / `nexabank_demo_schema_version` | Seeding sentinel and schema version |

## Fraud rules

`fraud-rule-engine.service.ts` is a direct port of the backend engine and its
six rules, with the same `FraudProperties` defaults:

| Rule | Trigger | Score |
|---|---|---|
| `LARGE_TRANSACTION` | amount > 50,000 | +25 |
| `HIGH_TRANSACTION_FREQUENCY` | more than 5 movements in 10 min | +20 |
| `RAPID_WITHDRAWALS` | 3 or more withdrawals in 10 min | +20 |
| `RAPID_TRANSFERS` | 3 or more outgoing transfers in 10 min | +20 |
| `NEW_BENEFICIARY` | payee created within 24 h | +15 |
| `ACCOUNT_ACTIVITY_SPIKE` | amount > 2× the recent average (min 3 of last 10) | +20 |

`REVIEW` at 30, `BLOCK` at 60. Bands: 0–29 `LOW`, 30–59 `MEDIUM`, 60–79 `HIGH`,
80–100 `CRITICAL`.

A `REVIEW` or `BLOCK` decision **holds** the movement: no ledger row is written,
no balance changes, and a fraud alert is raised instead. The alert workflow
(`OPEN → UNDER_REVIEW → RESOLVED | FALSE_POSITIVE`) enforces the same legal
transitions as the backend.

## Backend — kept as the enterprise reference

`backend/` is the original Spring Boot + PostgreSQL implementation. It is
**untouched and no longer required** to run the frontend, but it is kept
because it documents the production architecture this demo imitates: layered
monolith, JPA entities, bean validation, audit hooks, the authoritative fraud
engine, and the REST contracts the Angular services still mirror.

```powershell
# Only if you want to explore the reference implementation
cd nexa-bank
Copy-Item .env.example .env
docker compose up -d postgres
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

`docs/architecture.md`, `docs/api-design.md` and `docs/database-design.md`
describe that server-side design.

## Project purpose

Learn and demonstrate realistic enterprise banking engineering: layered Spring
Boot monolith, Angular SPA, PostgreSQL, audit/fraud hooks, and clean REST
contracts — plus a dependency-free local mode that lets the whole product be
explored in a browser.

## Technology stack

| Layer | Choice |
|---|---|
| Frontend (runs standalone) | Angular 22, TypeScript, standalone components, Router, Reactive Forms, Signals, **LocalStorage** |
| Backend (reference only) | Java 17, Spring Boot 4.1.1, Maven, Web / Data JPA / Validation / Actuator / Security, PostgreSQL driver, JUnit 5 + Mockito-ready, H2 (tests only) |
| Database (reference only) | PostgreSQL 17 (Docker) |
| Infra (reference only) | docker-compose (Postgres) |

The frontend no longer uses `HttpClient`: `HttpClient` and the auth/API
interceptors were removed with the REST layer, and `environment.apiBaseUrl` is
gone.

## Folder structure

```
nexa-bank/
  backend/            # Spring Boot (com.nexabank) — reference, not required
    src/main/java/com/nexabank/
      NexaBankApplication.java
      config/ controller/ service/ repository/ entity/ dto/
      exception/ security/ audit/ fraud/ transaction/
    src/main/resources/
      application.yml application-dev.yml application-prod.yml
    src/test/...
    pom.xml
  frontend/           # Angular 22 standalone — this is what you run
    src/app/
      core/
        data/         # demo-data.ts (seed dataset)
        services/     # local-storage, demo-repository, ledger,
                      # fraud-rule-engine, demo-api + feature services
      shared/         # reusable UI, models, utils
      features/       # dashboard accounts transactions transfers
                      # fraud customers admin
      app.ts          # root component
      app.routes.ts   # unguarded — every screen is open
    src/environments/ # environment.ts + environment.development.ts
  docs/               # architecture.md api-design.md database-design.md
  docker-compose.yml  # PostgreSQL — reference only
  .env.example
  .gitignore
  README.md
```

## Prerequisites

For local demo mode: **Node 20.19+/22.12+ (24 OK), npm, Angular CLI 22 (via
`npx`)**. That is the entire list.

For the backend reference implementation only: additionally Java 17, Maven 3.9+,
and Docker Desktop.

## Environment variables

None for local demo mode — the data lives in the browser, so there is nothing
to configure.

For the backend reference implementation:

| Var | Used by | Default |
|---|---|---|
| `DATABASE_URL` | backend | `jdbc:postgresql://localhost:5432/nexabank` |
| `DATABASE_USERNAME` | backend | `nexabank` |
| `DATABASE_PASSWORD` | backend | `nexabank` |
| `SPRING_PROFILES_ACTIVE` | backend | `dev` locally |
| `CORS_ALLOWED_ORIGINS` | backend | `http://localhost:4200` |
| `POSTGRES_DB/USER/PASSWORD` | compose | `nexabank/nexabank/nexabank` |

Never commit real secrets — copy `.env.example` to `.env` (gitignored).

## Development workflow

1. `cd frontend && npm install && npm start` — explore the local demo.
2. `cd backend && mvn test && mvn spring-boot:run` — only when working on the
   reference server.
3. Keep the local demo and the backend contracts in step: when a service method
   or error status changes, change it in both.
4. Document API/schema changes in `docs/`.

## Module status

Done: Customer management, Account management, Transactions, Transfers,
Beneficiaries, Fraud detection, Risk scoring, Fraud alerts, Audit logging,
Admin dashboard.

Removed for local demo mode: Authentication / JWT security (no session exists
to protect a browser-only store).

Not started: Payments, Notifications, Reporting, Observability, production
Security.
