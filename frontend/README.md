# NexaBank — Frontend (Angular SPA)

Angular 22 standalone application for NexaBank. It is the UI layer of the
full-stack platform and talks to the Spring Boot API over REST with a JWT
session.

```
Angular  →  JWT (Bearer)  →  Spring Boot REST API  →  PostgreSQL
```

You need the backend and its infrastructure running. See the root `README.md`.

## Run it

```bash
# 1. infrastructure + backend (from the repo root)
docker compose up -d
cd ../backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 2. frontend
cd frontend
npm install
npm start          # ng serve → http://localhost:4200
```

Production build and tests:

```bash
npm run build
npm test
```

Sign in with a seeded dev user (password `Password123!`):
`customer1`, `employee1`, `fraud1`, or `admin1`.

## Configuration

The API origin is set in `src/environments/`:

| File | Used by | `apiBaseUrl` |
|---|---|---|
| `environment.development.ts` | `ng serve` | `http://localhost:8080` |
| `environment.ts` | production build | `http://localhost:8080` |

Point `apiBaseUrl` at your deployed API when building for another environment.
The backend must allow the SPA origin via `CORS_ALLOWED_ORIGINS`.

## Architecture

### Auth & access control

- `core/services/auth.service.ts` — owns the session: `login`, `register`,
  `me`, `logout`, and the `user` / `isAuthenticated` / `role` signals. The JWT
  and safe user profile persist under `nexabank_access_token` and
  `nexabank_current_user`; no banking data is stored client-side.
- `core/interceptors/auth.interceptor.ts` — adds `Authorization: Bearer …` to
  API calls and redirects to `/login?returnUrl=…` on `401`.
- `core/guards/auth.guard.ts` — `authGuard` (session required) and `roleGuard`
  (checks `route.data.roles`). These mirror the backend’s `@PreAuthorize`
  rules; the server is the real boundary.

### Routes

`app.routes.ts` declares public `login`/`signup` and guards every other route
with the appropriate roles:

| Route | Allowed roles |
|---|---|
| `/dashboard`, `/accounts/:id` | any authenticated |
| `/accounts`, `/accounts/new`, `/customers*` | `BANK_EMPLOYEE`, `ADMIN` |
| `/transactions`, `/transfers` | `CUSTOMER`, `BANK_EMPLOYEE`, `ADMIN` |
| `/fraud`, `/fraud/:id` | `FRAUD_ANALYST`, `ADMIN` |
| `/admin` | `ADMIN` |

### Services

Feature services in `core/services/` are thin `HttpClient` wrappers that keep
the REST contracts and `Observable` return types the components expect:

| Service | Endpoints |
|---|---|
| `AuthService` | `/api/v1/auth/*` |
| `CustomerService` | `/api/v1/customers`, `/customers/me`, `/customers/{id}/accounts` |
| `AccountService` | `/api/v1/accounts`, `/accounts/me`, `/accounts/{id}/status` |
| `TransactionService` | deposits, withdrawals, per-account and own history |
| `TransferService` | `/api/v1/transfers` |
| `BeneficiaryService` | `/api/v1/beneficiaries` |
| `DashboardService` | `/api/v1/dashboard/summary` |
| `FraudService` | `/api/v1/fraud/**` |
| `HealthService` | `/api/v1/health` |

`HttpErrorResponse` surfaces the backend’s status codes (400, 401, 403, 404,
409, 422) and its `ApiError` body (`message`, `errors`), which the components
render via the shared `readError()` helper.

### Dashboard “Data Source” badge

`shared/components/backend-status/` probes `GET /api/v1/health` and shows
`API online`, `Checking…`, or `Unavailable`.
