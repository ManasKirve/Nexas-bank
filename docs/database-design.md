# NexaBank — Database Design (Phase 6)

> Phase 2 creates the first real tables: **customers** and **accounts**.
> No Flyway/Liquibase yet (per phase constraints); dev uses Hibernate
> `update`, prod stays on `validate` until migrations arrive.

## Engine

PostgreSQL 17 (Docker, `postgres:17-alpine`), localhost:5432.

- Database: `nexabank` (override via `POSTGRES_DB`)
- User: `nexabank` (override via `POSTGRES_USER` / `POSTGRES_PASSWORD`)
- Backend connects via `DATABASE_URL` (default
  `jdbc:postgresql://localhost:5432/nexabank`).

## Tables

### customers

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT (identity) | PK |
| customer_number | VARCHAR(16) | NOT NULL, UNIQUE (`uk_customers_customer_number`), e.g. `CUST-100001` |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | NOT NULL, UNIQUE (`uk_customers_email`), stored lowercase |
| phone | VARCHAR(32) | nullable |
| status | VARCHAR(16) | NOT NULL, enum `ACTIVE/INACTIVE/BLOCKED` |
| created_at | TIMESTAMP | NOT NULL, auto |
| updated_at | TIMESTAMP | NOT NULL, auto |

DELETE is soft — rows are never removed; status flips to `INACTIVE`.

### accounts

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT (identity) | PK |
| account_number | VARCHAR(24) | NOT NULL, UNIQUE (`uk_accounts_account_number`), e.g. `100000000001` |
| customer_id | BIGINT | NOT NULL, FK → `customers.id` (`fk_accounts_customer`) |
| account_type | VARCHAR(16) | NOT NULL, enum `SAVINGS/CHECKING` |
| balance | DECIMAL(19,2) | NOT NULL, always `0.00` in this phase |
| currency | VARCHAR(3) | NOT NULL, `INR` in this phase |
| status | VARCHAR(16) | NOT NULL, enum `ACTIVE/FROZEN/CLOSED` |
| created_at / updated_at | TIMESTAMP | NOT NULL, auto |

Indexes: `idx_accounts_customer_id`, `idx_accounts_status`.
Relationship: `Customer 1 → * Account` (`@OneToMany(mappedBy)` / `@ManyToOne(fetch = LAZY)`).
Money is `BigDecimal` everywhere — never `double`/`float`.
Phase 4: balances are mutated only by the transaction engine (pessimistic
row lock + `@Transactional`); account CRUD APIs still never touch balances.

### transactions (Phase 4 — immutable ledger)

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT (identity) | PK |
| transaction_reference | VARCHAR(32) | NOT NULL, UNIQUE (`uk_transactions_reference`), e.g. `TXN-20260918-000001` |
| idempotency_key | VARCHAR(128) | NOT NULL, UNIQUE (`uk_transactions_idempotency_key`), client-generated |
| account_id | BIGINT | NOT NULL, FK → `accounts.id` (`fk_transactions_account`) |
| type | VARCHAR(16) | NOT NULL, enum `DEPOSIT/WITHDRAWAL` |
| status | VARCHAR(16) | NOT NULL, enum `PENDING/COMPLETED/FAILED/REVERSED` |
| amount | DECIMAL(19,2) | NOT NULL, > 0 |
| currency | VARCHAR(3) | NOT NULL, `INR` |
| balance_before / balance_after | DECIMAL(19,2) | NOT NULL, ledger snapshot per movement |
| description | VARCHAR(500) | nullable |
| created_by_user_id | BIGINT | nullable, initiating app user |
| created_at | TIMESTAMP | NOT NULL, auto |
| completed_at | TIMESTAMP | nullable, set on completion |

Indexes: `idx_transactions_account_id`, `idx_transactions_created_at`,
`idx_transactions_account_created` (history newest-first).
Completed rows are never updated/deleted — reversals (future) create
separate compensating rows.
Phase 5 adds two nullable transfer columns: `transfer_reference` (indexed,
shared `TRF-…` per debit+credit pair) and `counterparty_account_number`
(other side's account number for history context).

### beneficiaries (Phase 5)

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT (identity) | PK |
| customer_id | BIGINT | NOT NULL, FK → `customers.id` (`fk_beneficiaries_customer`) |
| beneficiary_account_id | BIGINT | NOT NULL, FK → `accounts.id` (`fk_beneficiaries_account`) |
| nickname | VARCHAR(64) | NOT NULL, defaults to the account number |
| status | VARCHAR(16) | NOT NULL, enum `ACTIVE/DISABLED` |
| created_at / updated_at | TIMESTAMP | NOT NULL, auto |

Unique: `uk_beneficiaries_customer_account (customer_id, beneficiary_account_id)`.
Indexes: `idx_beneficiaries_customer_id`, `idx_beneficiaries_account_id`,
`idx_beneficiaries_status`. Deletion is a soft disable.

### fraud_evaluations (Phase 6 — historical, append-only)

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT (identity) | PK |
| account_id | BIGINT | NOT NULL, FK → `accounts.id` (`fk_fraud_eval_account`) |
| transaction_id | BIGINT | nullable, FK → `transactions.id` (`fk_fraud_eval_transaction`) — set only for APPROVE (completed ledger row); null for REVIEW/BLOCK attempts where no financial transaction completed |
| attempted_reference | VARCHAR(40) | nullable, `TXN-…`/`TRF-…` attempt reference (traceability without a ledger row) |
| transfer_reference | VARCHAR(32) | nullable, shared `TRF-…` for transfer attempts |
| idempotency_key | VARCHAR(128) | nullable, client key of the attempt |
| transaction_type | VARCHAR(16) | NOT NULL, enum `DEPOSIT/WITHDRAWAL/TRANSFER_DEBIT/TRANSFER_CREDIT` |
| amount / currency | DECIMAL(19,2) / VARCHAR(3) | NOT NULL, attempted amount (`INR`) |
| risk_score | INTEGER | NOT NULL, bounded 0–100 |
| risk_level | VARCHAR(16) | NOT NULL, enum `LOW/MEDIUM/HIGH/CRITICAL` |
| decision | VARCHAR(16) | NOT NULL, enum `APPROVE/REVIEW/BLOCK` |
| factors_json | TEXT | JSON array `[{code, description, scoreContribution}]` |
| evaluation_version | VARCHAR(16) | NOT NULL, e.g. `v1` — stamped per row |
| evaluation_reason | VARCHAR(500) | nullable, human summary |
| evaluated_by | VARCHAR(64) | nullable, initiating username |
| evaluated_at | TIMESTAMP | NOT NULL, auto |

Indexes: `idx_fraud_eval_account_id`, `idx_fraud_eval_transaction_id`,
`idx_fraud_eval_decision`, `idx_fraud_eval_evaluated_at`.

Why historical: evaluations are never updated or deleted. The
`evaluation_version` plus the frozen factor list means a future rule
change cannot rewrite what a past decision meant — the audit trail stays
explainable. APPROVE rows link to their ledger row; REVIEW/BLOCK rows
trace the held attempt via `attempted_reference`/account/amount.

### fraud_alerts (Phase 6 — analyst work items)

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT (identity) | PK |
| evaluation_id | BIGINT | NOT NULL, FK → `fraud_evaluations.id` (`fk_fraud_alert_evaluation`) |
| transaction_id | BIGINT | nullable, FK → `transactions.id` (normally null — held attempts have no completed row) |
| account_id | BIGINT | NOT NULL, FK → `accounts.id` (`fk_fraud_alert_account`) |
| severity | VARCHAR(16) | NOT NULL, enum `LOW/MEDIUM/HIGH/CRITICAL` (mirrors risk band) |
| status | VARCHAR(16) | NOT NULL, enum `OPEN/UNDER_REVIEW/RESOLVED/FALSE_POSITIVE` |
| reason | VARCHAR(500) | nullable, decision/factor summary, analyst notes on resolution |
| created_at | TIMESTAMP | NOT NULL, auto |
| reviewed_at | TIMESTAMP | nullable, set on every review action |
| reviewed_by | VARCHAR(64) | nullable, analyst username |

Indexes: `idx_fraud_alert_status`, `idx_fraud_alert_severity`,
`idx_fraud_alert_created_at`, `idx_fraud_alert_evaluation_id`.

Lifecycle (server-validated): `OPEN → UNDER_REVIEW → RESOLVED` or
`OPEN → UNDER_REVIEW → FALSE_POSITIVE`. Only REVIEW/BLOCK evaluations
create alerts; relationships: `Account 1 → * FraudEvaluation`,
`Transaction 1 → 0..1 FraudEvaluation`, `FraudEvaluation 1 → 0..1 FraudAlert`.

## Backend JPA settings (Phase 2)

- prod / base: `ddl-auto: validate` (unchanged, safe).
- dev: `ddl-auto: update` — creates the two tables for local work.
- `open-in-view: false`; tests use H2 (`MODE=PostgreSQL`, `create-drop`).

## Still planned (not created)

transfers (external rails), notifications — plus Flyway/Liquibase
migrations to replace dev `update`. (Phase 3 added `users`; Phase 4 added
`transactions`; Phase 5 added `beneficiaries` + transfer columns; Phase 6
added `fraud_evaluations` + `fraud_alerts`.)
