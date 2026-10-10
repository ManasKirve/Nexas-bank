# NexaBank — Enterprise Digital Banking & Fraud Monitoring Platform

> **Educational / portfolio build.** An original enterprise-style banking
> application for learning and interview preparation. It does **not** reproduce
> any real bank's proprietary systems, APIs, or business rules.

A full-stack banking platform:

```
Angular 22 SPA  →  JWT (Bearer)  →  Spring Boot 4 REST API  →  PostgreSQL 17
                                        │
                                        ├── Redis   (cache / rate limiting)
                                        └── Kafka   (async domain events, outbox)
```

Authentication is stateless JWT with role-based access control. The backend is
the authorization boundary; the SPA enforces the same roles for navigation and
UX only.

## Running it

### Prerequisites

- **Node 20.19+/22.12+ (24 OK)** and npm (Angular CLI 22 runs via `npx`)
- **Java 17** and **Maven 3.9+**
- **Docker Desktop** (for PostgreSQL/Redis/Kafka)

### 1. Start infrastructure

```powershell
cd nexa-bank
Copy-Item .env.example .env      # gitignored; adjust locally if you like
docker compose up -d             # postgres + redis + kafka
docker compose ps                # wait for postgres/redis to be healthy
```

### 2. Start the backend (API on :8080)

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile seeds four users (password `Password123!` unless
`NEXABANK_DEV_SEED_PASSWORD` is set):

| Username | Role | Can reach |
|---|---|---|
| `customer1` | `CUSTOMER` | Dashboard (own balances), Transactions, Transfers |
| `employee1` | `BANK_EMPLOYEE` | + Customers, Accounts management |
| `fraud1` | `FRAUD_ANALYST` | + Fraud monitoring console |
| `admin1` | `ADMIN` | Everything, including Admin |

**JWT secret.** The backend signs tokens with `JWT_SECRET` (HS256, ≥ 32 bytes).
Local `dev` falls back to an insecure built-in key; **production must set the
env var** or startup fails fast with *“JWT secret is not configured.”* Generate
one with `openssl rand -base64 48` and never commit it.

### 3. Start the frontend (SPA on :4200)

```powershell
cd frontend
npm install        # .npmrc sets --legacy-peer-deps to work around an npm 10.7 peer bug
npm start          # ng serve → http://localhost:4200
```

Open `http://localhost:4200`. You are redirected to `/login`; sign in with one
of the seeded users above. The app talks to the API at
`environment.apiBaseUrl` (`http://localhost:8080`).

## How authentication works

- **Login / signup** (`/login`, `/signup`) post to `/api/v1/auth/login` and
  `/api/v1/auth/register`. Registration always creates a `CUSTOMER`; privileged
  roles are assigned server-side, never by the client.
- The JWT and the safe user profile are stored under `nexabank_access_token`
  and `nexabank_current_user` (session data only — no banking data in storage).
- `authInterceptor` attaches `Authorization: Bearer <token>` to every request
  aimed at the API and, on a `401`, clears the session and redirects to
  `/login?returnUrl=…`.
- `authGuard` requires a session; `roleGuard` additionally checks the route’s
  `data.roles`. Both mirror the backend’s `@PreAuthorize` rules.

### Route access (RBAC)

| Route | Allowed roles |
|---|---|
| `/dashboard`, `/accounts/:id` | any authenticated user |
| `/accounts`, `/accounts/new`, `/customers*` | `BANK_EMPLOYEE`, `ADMIN` |
| `/transactions`, `/transfers` | `CUSTOMER`, `BANK_EMPLOYEE`, `ADMIN` |
| `/fraud`, `/fraud/:id` | `FRAUD_ANALYST`, `ADMIN` |
| `/admin` | `ADMIN` |

The server enforces these regardless of the client, so hiding a route is
convenience, not security.

## REST API surface

| Area | Base path |
|---|---|
| Auth | `/api/v1/auth` (`register`, `login`, `me`) |
| Customers | `/api/v1/customers`, `/api/v1/customers/me`, `/customers/{id}/accounts` |
| Accounts | `/api/v1/accounts`, `/accounts/me`, `/accounts/{id}/status` |
| Transactions | `/api/v1/accounts/{id}/deposits`, `.../withdrawals`, `.../transactions`, `/accounts/me/transactions` |
| Transfers | `/api/v1/transfers` |
| Beneficiaries | `/api/v1/beneficiaries` |
| Dashboard | `/api/v1/dashboard/summary` |
| Fraud | `/api/v1/fraud/alerts`, `/evaluations`, `/dashboard` |
| Health | `/api/v1/health` (public) |

Money-movement endpoints require a client-generated `idempotencyKey` so a
retried request cannot double-post.

## Fraud engine

The backend engine is authoritative; the console only reviews its output.
Six deterministic rules run before money moves:

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
(`OPEN → UNDER_REVIEW → RESOLVED | FALSE_POSITIVE`) enforces the legal
transitions on the server.

## Technology stack

| Layer | Choice |
|---|---|
| Frontend | Angular 22, TypeScript, standalone components, Router (guards), Reactive Forms, Signals, `HttpClient` + interceptors |
| Backend | Java 17, Spring Boot 4.1.1, Maven, Web / Data JPA / Validation / Actuator / Security, JWT, PostgreSQL driver, JUnit 5 |
| Database | PostgreSQL 17 (Docker) |
| Infra | docker-compose (PostgreSQL + Redis + Kafka) |

## Folder structure

```
nexa-bank/
  backend/            # Spring Boot (com.nexabank)
    src/main/java/com/nexabank/
      config/ controller/ service/ repository/ entity/ dto/
      exception/ security/ audit/ fraud/ transaction/
    src/main/resources/
      application.yml application-dev.yml application-prod.yml
    src/test/...
    pom.xml
  frontend/           # Angular 22 standalone SPA
    src/app/
      core/
        guards/       # authGuard, roleGuard
        interceptors/ # authInterceptor (Bearer + 401 handling)
        services/     # HttpClient feature services + AuthService
      shared/         # reusable UI, models, utils
      features/       # dashboard accounts transactions transfers
                      # fraud customers auth admin
      app.routes.ts   # public login/signup + guarded routes (data.roles)
      app.config.ts   # provideRouter + provideHttpClient(withInterceptors)
    src/environments/ # environment.ts + environment.development.ts (apiBaseUrl)
  docs/               # architecture.md api-design.md database-design.md
  docker-compose.yml  # PostgreSQL + Redis + Kafka
  .env.example
  .gitignore
  README.md
```

## Environment variables

Copy `.env.example` to `.env` (gitignored). Backend-relevant vars:

| Var | Used by | Default |
|---|---|---|
| `DATABASE_URL` | backend | `jdbc:postgresql://localhost:5432/nexabank` |
| `DATABASE_USERNAME` | backend | `nexabank` |
| `DATABASE_PASSWORD` | backend | `nexabank` |
| `SPRING_PROFILES_ACTIVE` | backend | `dev` locally |
| `CORS_ALLOWED_ORIGINS` | backend | `http://localhost:4200` |
| `JWT_SECRET` | backend | insecure dev fallback; **required in prod** |
| `JWT_EXPIRATION_MS` | backend | `3600000` (1 h) |
| `POSTGRES_DB/USER/PASSWORD` | compose | `nexabank/nexabank/nexabank` |
| `REDIS_*`, `KAFKA_*`, `APP_*` | backend | see `.env.example` |

Never commit real secrets — `.env` is gitignored.

The frontend’s API origin lives in `frontend/src/environments/environment*.ts`
(`apiBaseUrl`).

## Development workflow

1. `docker compose up -d` then `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
2. `cd frontend && npm install && npm start`.
3. Backend: `mvn test`. Frontend: `npm test`.
4. Keep the Angular service layer and the backend REST contracts in step.
5. Document API/schema changes in `docs/`.

## Module status

Done: Authentication (JWT + RBAC), Customer management, Account management,
Transactions, Transfers, Beneficiaries, Fraud detection, Risk scoring, Fraud
alerts, Audit logging, Admin dashboard.

Not started: Payments, Notifications, Reporting, Observability, production
hardening.
