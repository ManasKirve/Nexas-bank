# NexaBank — Frontend (LocalStorage Demo)

This is the **only** part of NexaBank you need to run. It is a standalone
Angular application: all data lives in your browser's LocalStorage.

You do **not** need Docker, PostgreSQL, the Spring Boot backend, `JWT_SECRET`,
a login, or a signup.

## Run it

```bash
cd frontend
npm install
npm start
```

Then open <http://localhost:4200/>. The app opens straight on the Dashboard.

Production build:

```bash
npm run build
```

Unit tests:

```bash
npm test
```

## How this app is different from the original

The Docker build was `Angular → JWT → Spring Boot → PostgreSQL`. Authentication
has been removed on purpose for this local demo, so the app is now:

```
Angular → LocalStorage
```

Concretely:

- no login, signup, logout, token, or fake user;
- no `authGuard` / `roleGuard` — every screen is reachable;
- no `Authorization` header, because there is no server to send it to;
- no `HttpClient` at runtime; the feature services talk to LocalStorage and
  still return `Observable`s, failing with `HttpErrorResponse`s carrying the
  original status codes (400, 404, 409, 422);
- the dashboard "Data Source" badge now reports whether LocalStorage is ready
  instead of whether the API is reachable.

**Admin → Acting demo customer** selects whose money the "my accounts", "my
transactions" and "beneficiaries" screens show. It is a data filter, not a
session — it is how the app knows whose ledger to read now that there is no JWT
to derive ownership from.

## Data layer

All persistence goes through one gateway,
`src/app/core/services/local-storage.service.ts`. No component or feature
service touches `localStorage` directly.

| Key | Contents |
|---|---|
| `nexabank_customers` | Customer records |
| `nexabank_accounts` | Accounts with running balances |
| `nexabank_transactions` | Ledger: deposits, withdrawals, both transfer legs |
| `nexabank_beneficiaries` | Payees owned by the acting customer |
| `nexabank_fraud_alerts` | Alert queue and workflow state |
| `nexabank_fraud_evaluations` | Every engine decision, including approvals |
| `nexabank_audit_log` | Demo activity trail |
| `nexabank_active_customer_id` | The acting demo customer |

The dataset is seeded **once**, on first launch only (guarded by
`nexabank_demo_initialized` and a schema version). Later launches and browser
refreshes never overwrite what you did. **Admin → Reset Demo Data** wipes and
re-seeds on demand.

## Banking operations

Deposit, withdrawal, transfer, beneficiary create/disable, transaction history
and balance updates all run against LocalStorage. Every movement passes through
one fraud gate before any balance moves, and a held movement writes no ledger
row and raises an alert instead.

## About `../backend`

`../backend` is the original Spring Boot + PostgreSQL implementation, kept
**untouched as reference**. It documents the production architecture this demo
imitates: layered monolith, JPA entities, bean validation, audit hooks, the
authoritative fraud engine, stateless JWT security, and the REST contracts the
Angular services still mirror.

It is not required, and the frontend never calls it. If you ever choose to run
it yourself it still expects its own infrastructure (`JWT_SECRET`, PostgreSQL,
etc.) — that is intentional, so the reference build stays faithful to the
production design.
