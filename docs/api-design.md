# NexaBank — API Design (Phase 6)

Base URL: `http://localhost:8080`
Versioning: `/api/v1/...` (all future banking endpoints versioned from day one).

## Conventions (binding for later phases)

- REST nouns, plural: `/api/v1/accounts`, `/api/v1/transactions`, `/api/v1/transfers`.
- DTOs only — never expose JPA entities.
- Validation via Jakarta Validation (`@Valid`); errors return `ApiError`.
- Standard error body (`ApiError`):

```json
{
  "timestamp": "2026-09-18T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Human-readable detail",
  "path": "/api/v1/transfers"
}
```

- Idempotency (planned): `Idempotency-Key` header on POST transfer/payment endpoints.
- Auth: stateless Bearer JWT + RBAC; `401` unauthenticated, `403` forbidden.
  Every protected request carries `Authorization: Bearer <accessToken>`.

## Phase 6 endpoints — Fraud Detection & Transaction Risk Engine (`/api/v1/fraud`)

Deterministic, explainable risk pipeline (educational thresholds — NOT
production banking standards):

Transaction → Fraud Evaluation → Risk Factors → Rule Evaluation →
Risk Score (0–100) → Risk Decision → Persist Fraud Evaluation → Audit →
Fraud Analyst Dashboard.

All endpoints require `Authorization: Bearer <accessToken>` with role
`FRAUD_ANALYST` or `ADMIN` (enforced at the URL level in `SecurityConfig`
plus method security). `CUSTOMER` and `BANK_EMPLOYEE` receive `403`;
missing/invalid tokens receive `401`. Fraud evaluations and alerts are
never exposed to customers.

### Decisions

- `APPROVE` (score 0–29): the transaction proceeds normally; an
  evaluation is persisted and linked to the completed ledger row.
- `REVIEW` (score 30–59): the transaction is HELD — no balance mutation,
  no completed ledger row. An evaluation plus an `OPEN` alert persist and
  an analyst must review. The customer sees only the generic message
  `"Your transaction could not be completed at this time."` (`422`) —
  never the score, rules, factors or analyst data.
- `BLOCK` (score 60–100): same safe behavior as `REVIEW` with higher
  severity; money never moves.

Thresholds (`fraud.review-threshold`, `fraud.block-threshold`) and every
rule threshold/score live in `application.yml` (`FraudProperties`) —
no magic numbers in rule code.

### Risk levels (score bands, educational)

`0–29 LOW`, `30–59 MEDIUM`, `60–79 HIGH`, `80–100 CRITICAL`. Severity on
alerts mirrors the evaluation band.

### GET /api/v1/fraud/alerts (analyst/admin)

Review queue, newest first: `?status=OPEN&page=0&size=20` (size capped at
100). Omit `status` for all alerts.

Response `200` (`FraudAlertPageResponse`):

```json
{ "content": [ { "id": 7, "severity": "HIGH", "status": "OPEN",
  "reason": "Decision REVIEW with score 40: LARGE_TRANSACTION, NEW_BENEFICIARY",
  "createdAt": "2026-09-19T10:00:00Z", "reviewedAt": null, "reviewedBy": null,
  "accountId": 3, "accountNumber": "100000000001", "transactionId": null,
  "evaluationId": 11, "riskScore": 40, "decision": "REVIEW",
  "riskLevel": "MEDIUM", "amount": 60000.00, "currency": "INR",
  "attemptedReference": "TRF-20260919-000001",
  "transferReference": "TRF-20260919-000001", "evaluationVersion": "v1",
  "factors": [ { "code": "LARGE_TRANSACTION",
    "description": "Transaction exceeded configured threshold",
    "scoreContribution": 25 } ] } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```

### GET /api/v1/fraud/alerts/{id} (analyst/admin)

One alert with full explainable context (reference, account, amount,
score, decision, severity, factors, version, status, analyst actions).
Missing id → `404`.

### POST /api/v1/fraud/alerts/{id}/review (analyst/admin)

`OPEN → UNDER_REVIEW`. Records analyst username + timestamp. Any other
current status → `400`. Optional body: `{ "reason": "..." }` (may be
omitted entirely).

### POST /api/v1/fraud/alerts/{id}/resolve (analyst/admin)

`UNDER_REVIEW → RESOLVED`. Records analyst + timestamp (+ optional
reason). Direct `OPEN → RESOLVED` → `400`; terminal states accept no
further transitions (`400`).

### POST /api/v1/fraud/alerts/{id}/false-positive (analyst/admin)

`UNDER_REVIEW → FALSE_POSITIVE`. Same transition rules as resolve.

### GET /api/v1/fraud/evaluations (analyst/admin)

Historical append-only evaluations, newest first:
`?decision=BLOCK&page=0&size=20`. Every row carries `evaluationVersion`
(`v1`) so future rule changes never rewrite history.

### GET /api/v1/fraud/evaluations/{id} (analyst/admin)

One historical evaluation. Missing id → `404`.

### GET /api/v1/fraud/dashboard (analyst/admin)

Queue counts plus recent work:

```json
{ "openAlerts": 2, "underReviewAlerts": 1, "resolvedAlerts": 5,
  "falsePositiveAlerts": 1, "highRiskAlerts": 1, "blockedEvaluations": 1,
  "reviewEvaluations": 2, "recentAlerts": [ { "...": "..." } ] }
```

### Fraud errors

- `400`: invalid alert transition, malformed body.
- `401`: missing/invalid/expired token.
- `403`: valid auth but wrong role (`CUSTOMER`, `BANK_EMPLOYEE` on
  `/fraud/**`).
- `404`: unknown alert/evaluation id.
- `422`: transaction held/blocked by fraud (customer-safe generic message
  only, on the money-movement endpoints — not on `/fraud/**`).

## Phase 5 endpoints — Beneficiaries & Transfers

Beneficiaries are customer-scoped named destination accounts; transfers
settle atomically as paired ledger legs. All endpoints require
`Authorization: Bearer <accessToken>`. Money movement and beneficiary
management require `CUSTOMER` (own data), `BANK_EMPLOYEE` or `ADMIN` —
`FRAUD_ANALYST` is denied (403).

### POST /api/v1/beneficiaries (CUSTOMER-owned; staff read/disable)

Request:

```json
{ "accountId": 12, "nickname": "My Savings" }
```

The owner is derived from the JWT (CUSTOMER) — never from the payload.
The destination account must exist and be ACTIVE. Re-adding a disabled
beneficiary re-activates the same row; an active duplicate returns `409`.

Response `201` (`BeneficiaryResponse`):

```json
{ "id": 1, "customerId": 7, "beneficiaryAccountId": 12,
  "beneficiaryAccountNumber": "100000000002", "nickname": "My Savings",
  "status": "ACTIVE", "createdAt": "2026-09-18T10:00:00Z",
  "updatedAt": "2026-09-18T10:00:00Z" }
```

### GET /api/v1/beneficiaries

Own list for CUSTOMER callers; full list for staff operational access.

### GET /api/v1/beneficiaries/{id}

`404` when missing; `403` for another customer's beneficiary.

### DELETE /api/v1/beneficiaries/{id}

Soft disable (`ACTIVE → DISABLED`, idempotent); history preserved.

### POST /api/v1/transfers (money-moving roles)

Atomic debit + credit. Both legs lock in ascending account-id order
(deadlock-safe), persist together, and share one `TRF-…` reference.

Request:

```json
{ "sourceAccountId": 3, "beneficiaryId": 1, "amount": 1000.00,
  "description": "Transfer", "idempotencyKey": "client-generated-key" }
```

Response `201` (`TransferResponse`):

```json
{ "transferReference": "TRF-20260918-000001",
  "sourceAccountId": 3, "sourceAccountNumber": "100000000001",
  "destinationAccountId": 4, "destinationAccountNumber": "100000000002",
  "amount": 1000.00, "currency": "INR",
  "sourceBalanceBefore": 5000.00, "sourceBalanceAfter": 4000.00,
  "destinationBalanceBefore": 200.00, "destinationBalanceAfter": 1200.00,
  "status": "COMPLETED", "createdAt": "2026-09-18T10:00:00Z" }
```

Validation/business errors:

- `400`: amount missing/≤0/more than 2dp, blank idempotency key.
- `401`: missing/invalid/expired token.
- `403`: another customer's source or beneficiary, `FRAUD_ANALYST` transfer.
- `404`: unknown source account or beneficiary.
- `409`: idempotency key reused with a different operation.
- `422`: insufficient funds, inactive source/destination, disabled
  beneficiary, self-transfer (source == destination), non-INR currency.

### Idempotency

The debit leg carries the client key, the credit leg a derived key
(unique constraints, server-enforced). An exact replay returns the
original `201` result without moving money again; concurrent duplicates
resolve to a single settled transfer.

### Transfer history

Transfer legs appear in the existing history endpoints as
`TRANSFER_DEBIT` / `TRANSFER_CREDIT` with the shared `transferReference`
and the counterparty account number (no private customer data exposed).

## Phase 4 endpoints — Transactions (`/api/v1/accounts`)

Core banking ledger: deposits, withdrawals and history. All endpoints
require `Authorization: Bearer <accessToken>`. Money movement additionally
requires role `CUSTOMER` (own accounts only), `BANK_EMPLOYEE` or `ADMIN` —
`FRAUD_ANALYST` is denied (403). Amounts are `BigDecimal` (2dp, INR only);
completed ledger rows are immutable (no PUT/DELETE).

### POST /api/v1/accounts/{accountId}/deposits (money-moving roles)

Request:

```json
{ "amount": 1000.00, "description": "Cash deposit", "idempotencyKey": "unique-client-generated-key" }
```

Response `201` (`TransactionResponse`):

```json
{ "transactionReference": "TXN-20260918-000001", "accountId": 3,
  "accountNumber": "100000000001", "transactionType": "DEPOSIT",
  "amount": 1000.00, "currency": "INR", "balanceBefore": 5000.00,
  "balanceAfter": 6000.00, "status": "COMPLETED",
  "description": "Cash deposit",
  "createdAt": "2026-09-18T10:00:00Z", "completedAt": "2026-09-18T10:00:01Z" }
```

### POST /api/v1/accounts/{accountId}/withdrawals (money-moving roles)

Request:

```json
{ "amount": 500.00, "description": "ATM withdrawal", "idempotencyKey": "unique-client-generated-key" }
```

Response `201`: same shape with `transactionType: WITHDRAWAL`
(`balanceBefore: 5000.00 → balanceAfter: 4500.00`).

Insufficient funds → `422` (balance untouched, no ledger row):

```json
{ "timestamp": "2026-09-18T10:00:00Z", "status": 422,
  "error": "Unprocessable Entity", "message": "Insufficient funds: available 100.00 INR",
  "path": "/api/v1/accounts/3/withdrawals" }
```

### GET /api/v1/accounts/{accountId}/transactions (authenticated + ownership)

Newest-first paginated history: `?page=0&size=20` (size capped at 100).
CUSTOMER callers may only read owned accounts; `BANK_EMPLOYEE`/`ADMIN`
have broader access.

Response `200` (`TransactionPageResponse`):

```json
{ "content": [ { "transactionReference": "TXN-20260918-000001", "...": "..." } ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1 }
```

### GET /api/v1/accounts/me/transactions (authenticated CUSTOMER)

History across the caller's own accounts; ownership derived from the JWT.

### Validation / business errors

- `400`: amount missing/≤0/more than 2dp, idempotency key blank, bad paging.
- `401`: missing/invalid/expired token.
- `403`: valid auth but denied — other customer's account/history,
  `FRAUD_ANALYST` attempting deposits/withdrawals or history reads.
- `404`: unknown account.
- `409`: idempotency key reused with a different operation.
- `422`: insufficient funds, inactive (non-ACTIVE) account, currency mismatch.

### Idempotency

The client generates `idempotencyKey` per operation (unique constraint,
server-enforced). An exact replay returns the original `201` result without
moving money again; a conflicting reuse returns `409`. Concurrent duplicate
submissions resolve to a single ledger row via the constraint + re-read.

## Phase 3 endpoints — Authentication (`/api/v1/auth`)

### POST /api/v1/auth/register (public)

Creates a CUSTOMER user only — there is no role field, so public
registration can never mint staff roles. Links an existing customer
record when `customerId` is supplied.

Request:

```json
{ "username": "customer1", "email": "customer1@example.com", "password": "Password123!", "customerId": 1 }
```

Response `201` (`UserResponse` — never contains any password material):

```json
{ "id": 1, "username": "customer1", "email": "customer1@example.com",
  "role": "CUSTOMER", "status": "ACTIVE", "customerId": 1 }
```

Errors: `400` validation, `404` unknown `customerId`, `409` duplicate username/email.

### POST /api/v1/auth/login (public)

All credential failures (unknown user, wrong password, non-ACTIVE status)
return the same generic `401` message to avoid account enumeration.

Request:

```json
{ "username": "customer1", "password": "Password123!" }
```

Response `200`:

```json
{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 3600,
  "user": { "id": 1, "username": "customer1", "email": "customer1@example.com",
            "role": "CUSTOMER", "status": "ACTIVE", "customerId": 1 } }
```

### GET /api/v1/auth/me (authenticated)

Returns the `UserResponse` for the JWT identity. Missing/invalid/expired
token → `401`.

### JWT authentication

- Header: `Authorization: Bearer <accessToken>`.
- Claims: `sub`, `username`, `role`, `iat`, `exp`.
- Lifetime: `app.jwt.expiration-ms` (default 1h, short-lived access token).
- Secret: `JWT_SECRET` env var (≥32 bytes); the backend fails fast at
  startup when it is missing. See `.env.example`.

### 401 Unauthorized vs 403 Forbidden

- `401`: no token, malformed/expired/invalid token, or inactive user —
  authentication is missing or unusable.
- `403`: valid authentication, but the role or ownership check denies the
  operation (e.g. CUSTOMER reading another customer's id, CUSTOMER calling
  an employee-only endpoint, wrong role for `/fraud/**` or `/admin/**`).

Both use the standard `ApiError` body; no stack traces are exposed.

## Phase 3 — authorization model

| Area | Rule (backend-enforced) |
|---|---|
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/health`, `GET /actuator/health` | Public |
| `GET /api/v1/customers/me`, `GET /api/v1/accounts/me` | Authenticated; ownership derived from the JWT, never from frontend input |
| `GET /api/v1/customers/{id}`, `GET /api/v1/accounts/{id}`, `GET /api/v1/customers/{id}/accounts` | Authenticated + ownership: CUSTOMER only own linked id; BANK_EMPLOYEE/ADMIN broader |
| `POST/PUT/DELETE /api/v1/customers/**`, `POST /api/v1/accounts`, `PUT /api/v1/accounts/{id}/status` | `BANK_EMPLOYEE` or `ADMIN` |
| `GET /api/v1/dashboard/summary` | Authenticated (any role) |
| `/api/v1/fraud/**` | `FRAUD_ANALYST` or `ADMIN` (endpoints land in a later phase) |
| `/api/v1/admin/**` | `ADMIN` (endpoints land in a later phase) |

## Phase 1 endpoints

### GET /api/v1/health

Connectivity probe for frontend + smoke tests.

Response `200`:

```json
{
  "status": "UP",
  "service": "nexabank-backend",
  "timestamp": "2026-09-18T10:00:00Z"
}
```

### GET /actuator/health

Spring Boot Actuator health (infra/observability).

Response `200`:

```json
{ "status": "UP" }
```

## Phase 2 endpoints — Customers & Accounts

### Customers (`/api/v1/customers`)

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/v1/customers` | `201` + `CustomerResponse` | Validated; duplicate email → `409`; **BANK_EMPLOYEE/ADMIN only** |
| GET | `/api/v1/customers` | `200` + `CustomerResponse[]` | Ordered by id; **BANK_EMPLOYEE/ADMIN only** |
| GET | `/api/v1/customers/me` | `200` | Own profile from the JWT identity |
| GET | `/api/v1/customers/{id}` | `200` | Missing → `404`; other customer's id → `403` for CUSTOMER callers |
| PUT | `/api/v1/customers/{id}` | `200` | Full update, validated; email stays unique; **BANK_EMPLOYEE/ADMIN only** |
| DELETE | `/api/v1/customers/{id}` | `200` | **Soft delete**: status → `INACTIVE`, row kept; **BANK_EMPLOYEE/ADMIN only** |

`CreateCustomerRequest`: `firstName*`, `lastName*`, `email*` (valid format), `phone` (optional, 7–32 chars).
`CustomerResponse`: `id`, `customerNumber` (`CUST-100001…`), names, email, phone, `status`, `createdAt`, `updatedAt`.
Validation failures return `400` with `errors: { field: message }`.

### Accounts (`/api/v1/accounts`)

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/v1/accounts` | `201` + `AccountResponse` | `{customerId, accountType, currency=INR}`; balance always `0`; missing customer → `404`; **BANK_EMPLOYEE/ADMIN only** |
| GET | `/api/v1/accounts` | `200` + `AccountResponse[]` | Ordered by id; **BANK_EMPLOYEE/ADMIN only** |
| GET | `/api/v1/accounts/me` | `200` | Own accounts from the JWT identity |
| GET | `/api/v1/accounts/{id}` | `200` | Missing → `404`; unowned account → `403` for CUSTOMER callers |
| GET | `/api/v1/customers/{customerId}/accounts` | `200` | Missing customer → `404`; other customer → `403` for CUSTOMER callers |
| PUT | `/api/v1/accounts/{id}/status` | `200` | `{status}`; illegal transition → `400`; **BANK_EMPLOYEE/ADMIN only** |

Allowed status transitions: `ACTIVE → FROZEN`, `FROZEN → ACTIVE`,
`ACTIVE → CLOSED`, `FROZEN → CLOSED`. `CLOSED` is terminal.

### Dashboard (`/api/v1/dashboard`)

| Method | Path | Success | Notes |
|---|---|---|---|
| GET | `/api/v1/dashboard/summary` | `200` | Live DB counts: `totalCustomers`, `totalAccounts`, `activeAccounts`, `frozenAccounts` |

## Planned modules (not implemented yet)

| Area | Sketch |
|---|---|
| Auth | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me` (done); refresh/logout (later, stateless for now) |
| Customers | `GET /api/v1/customers/{id}/accounts` (done); PATCH + search (later) |
| Accounts | Done (above); `GET /api/v1/accounts/{id}/transactions` (later) |
| Transactions | `GET /api/v1/transactions/{id}`, `GET /api/v1/transactions?accountId=...` |
| Transfers | `POST /api/v1/transfers` (idempotent), `GET /api/v1/transfers/{id}` |
| Fraud | `GET /api/v1/fraud/alerts`, `POST /api/v1/fraud/alerts/{id}/review` |
| Audit | `GET /api/v1/admin/audit-events?...` |
| Admin | `GET /api/v1/admin/...`, reporting endpoints |
