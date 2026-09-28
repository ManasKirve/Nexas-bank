/**
 * Phase 6 fraud detection and transaction risk engine:
 * Transaction -&gt; Fraud Evaluation -&gt; Risk Factors -&gt; Rule Evaluation -&gt;
 * Risk Score (0-100) -&gt; Risk Decision (APPROVE/REVIEW/BLOCK) -&gt;
 * Persist Fraud Evaluation -&gt; Audit -&gt; Fraud Analyst Dashboard.
 *
 * <p>Deterministic and explainable: modular {@code FraudRule} strategies,
 * centralized {@code FraudProperties} configuration, historical
 * {@code FraudEvaluation} rows and an analyst {@code FraudAlert} workflow.
 * The engine never mutates balances — it gates {@code TransactionService}
 * and {@code TransferService} before any ledger insert or balance change.</p>
 */
package com.nexabank.fraud;
