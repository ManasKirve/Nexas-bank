import { Injectable } from '@angular/core';
import {
  FraudDecision,
  RiskFactor,
  RiskLevel,
  factorScore,
} from '../../shared/models/fraud.model';
import { isAfter, round2 } from './demo-api';

/**
 * Deterministic fraud rule engine — a direct port of the Spring Boot
 * `FraudRuleEngine` and its six `FraudRule` implementations.
 *
 * Every threshold and score contribution below is the same educational default
 * the backend shipped in `FraudProperties`, and each rule keeps its original
 * code, description and trigger condition. The goal is a faithful local demo of
 * the existing NexaBank risk pipeline, not a production fraud engine.
 *
 * The engine is pure: it never mutates balances and never reads storage. The
 * caller decides what to do with the returned decision.
 */

/** Mirrors the backend `FraudProperties` configuration. */
export interface FraudRuleConfig {
  evaluationVersion: string;
  reviewThreshold: number;
  blockThreshold: number;
  largeTransactionThreshold: number;
  largeTransactionScore: number;
  highFrequencyCount: number;
  highFrequencyWindowMinutes: number;
  highFrequencyScore: number;
  rapidWithdrawalCount: number;
  rapidWithdrawalWindowMinutes: number;
  rapidWithdrawalScore: number;
  rapidTransferCount: number;
  rapidTransferWindowMinutes: number;
  rapidTransferScore: number;
  newBeneficiaryWindowHours: number;
  newBeneficiaryScore: number;
  activitySpikeScore: number;
  activitySpikeMultiplier: number;
  activitySpikeMinHistory: number;
  activitySpikeLookback: number;
}

export const DEFAULT_FRAUD_RULE_CONFIG: FraudRuleConfig = {
  evaluationVersion: 'v1',
  reviewThreshold: 30,
  blockThreshold: 60,
  largeTransactionThreshold: 50000,
  largeTransactionScore: 25,
  highFrequencyCount: 5,
  highFrequencyWindowMinutes: 10,
  highFrequencyScore: 20,
  rapidWithdrawalCount: 3,
  rapidWithdrawalWindowMinutes: 10,
  rapidWithdrawalScore: 20,
  rapidTransferCount: 3,
  rapidTransferWindowMinutes: 10,
  rapidTransferScore: 20,
  newBeneficiaryWindowHours: 24,
  newBeneficiaryScore: 15,
  activitySpikeScore: 20,
  activitySpikeMultiplier: 2.0,
  activitySpikeMinHistory: 3,
  activitySpikeLookback: 10,
};

/** Attempted movement types that get evaluated. TRANSFER_CREDIT never does. */
export type EvaluableType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER_DEBIT';

/** Pre-loaded context shared by every rule, exactly like the backend record. */
export interface FraudEvaluationContext {
  accountId: number;
  accountNumber: string;
  amount: number;
  type: EvaluableType;
  beneficiaryId: number | null;
  beneficiaryCreatedAt: string | null;
  /** Bounded recent COMPLETED movements for the account, newest first. */
  recentTransactions: Array<{ transactionType: string; amount: number; createdAt: string }>;
  now: Date;
}

export interface FraudOutcome {
  riskScore: number;
  riskLevel: RiskLevel;
  decision: FraudDecision;
  factors: RiskFactor[];
}

interface Rule {
  code: string;
  description: string;
  scoreContribution: number;
  evaluate: (context: FraudEvaluationContext, config: FraudRuleConfig) => boolean;
}

const MINUTE_MS = 60_000;
const HOUR_MS = 3_600_000;

function since(context: FraudEvaluationContext, windowMs: number, type: string): number {
  const cutoff = context.now.getTime() - windowMs;
  return context.recentTransactions.filter(
    (t) => t.transactionType === type && isAfter(t.createdAt, cutoff),
  ).length;
}

/** Rule 1 — Large Transaction. */
const LARGE_TRANSACTION: Rule = {
  code: 'LARGE_TRANSACTION',
  description: 'Transaction exceeded configured threshold',
  scoreContribution: 0, // filled from config
  evaluate: (context, config) => context.amount > config.largeTransactionThreshold,
};

/** Rule 2 — High Transaction Frequency: more than N movements in the window. */
const HIGH_FREQUENCY: Rule = {
  code: 'HIGH_TRANSACTION_FREQUENCY',
  description: 'Unusually high number of transactions in a short window',
  scoreContribution: 0,
  evaluate: (context, config) =>
    context.recentTransactions.filter(
      (t) => isAfter(t.createdAt, context.now.getTime() - config.highFrequencyWindowMinutes * MINUTE_MS),
    ).length > config.highFrequencyCount,
};

/** Rule 3 — Rapid Withdrawals: N settled withdrawals already inside the window. */
const RAPID_WITHDRAWALS: Rule = {
  code: 'RAPID_WITHDRAWALS',
  description: 'Multiple withdrawals occurred within the configured window',
  scoreContribution: 0,
  evaluate: (context, config) =>
    context.type === 'WITHDRAWAL' &&
    since(context, config.rapidWithdrawalWindowMinutes * MINUTE_MS, 'WITHDRAWAL') >=
      config.rapidWithdrawalCount,
};

/** Rule 4 — Rapid Transfers: N settled outgoing transfer legs inside the window. */
const RAPID_TRANSFERS: Rule = {
  code: 'RAPID_TRANSFERS',
  description: 'Multiple transfers occurred within the configured window',
  scoreContribution: 0,
  evaluate: (context, config) =>
    context.type === 'TRANSFER_DEBIT' &&
    since(context, config.rapidTransferWindowMinutes * MINUTE_MS, 'TRANSFER_DEBIT') >=
      config.rapidTransferCount,
};

/** Rule 5 — New Beneficiary: target payee was created inside the window. */
const NEW_BENEFICIARY: Rule = {
  code: 'NEW_BENEFICIARY',
  description: 'Beneficiary was recently created',
  scoreContribution: 0,
  evaluate: (context, config) =>
    context.type === 'TRANSFER_DEBIT' &&
    context.beneficiaryCreatedAt !== null &&
    Date.parse(context.beneficiaryCreatedAt) >
      context.now.getTime() - config.newBeneficiaryWindowHours * HOUR_MS,
};

/**
 * Rule 6 — Account Activity Spike: the amount dwarfs the account's recent
 * average. A minimum history size keeps single-movement accounts from
 * spiking on their first transaction.
 */
const ACTIVITY_SPIKE: Rule = {
  code: 'ACCOUNT_ACTIVITY_SPIKE',
  description: 'Transaction amount is significantly higher than the recent average',
  scoreContribution: 0,
  evaluate: (context, config) => {
    const baseline = context.recentTransactions.slice(0, Math.max(1, config.activitySpikeLookback));
    if (baseline.length < config.activitySpikeMinHistory) {
      return false;
    }
    const total = baseline.reduce((sum, t) => sum + t.amount, 0);
    const average = round2(total / baseline.length);
    if (average <= 0) {
      return false;
    }
    return context.amount > round2(average * config.activitySpikeMultiplier);
  },
};

const RULES: Array<{ rule: Rule; score: (config: FraudRuleConfig) => number }> = [
  { rule: LARGE_TRANSACTION, score: (c) => c.largeTransactionScore },
  { rule: HIGH_FREQUENCY, score: (c) => c.highFrequencyScore },
  { rule: RAPID_WITHDRAWALS, score: (c) => c.rapidWithdrawalScore },
  { rule: RAPID_TRANSFERS, score: (c) => c.rapidTransferScore },
  { rule: NEW_BENEFICIARY, score: (c) => c.newBeneficiaryScore },
  { rule: ACTIVITY_SPIKE, score: (c) => c.activitySpikeScore },
];

/** 0–29 LOW, 30–59 MEDIUM, 60–79 HIGH, 80–100 CRITICAL (backend `RiskLevel`). */
export function riskLevelFromScore(score: number): RiskLevel {
  if (score >= 80) {
    return 'CRITICAL';
  }
  if (score >= 60) {
    return 'HIGH';
  }
  if (score >= 30) {
    return 'MEDIUM';
  }
  return 'LOW';
}

/** Decision thresholds come from config; APPROVE below the review threshold. */
export function decisionFromScore(score: number, config: FraudRuleConfig): FraudDecision {
  if (score >= config.blockThreshold) {
    return 'BLOCK';
  }
  if (score >= config.reviewThreshold) {
    return 'REVIEW';
  }
  return 'APPROVE';
}

@Injectable({ providedIn: 'root' })
export class FraudRuleEngine {
  readonly config = DEFAULT_FRAUD_RULE_CONFIG;

  /**
   * Runs every rule against the pre-loaded context, sums the triggered
   * contributions, bounds the total to 0–100 and derives the risk band and the
   * decision. The caller decides whether to move money.
   */
  evaluate(context: FraudEvaluationContext, config: FraudRuleConfig = this.config): FraudOutcome {
    const factors: RiskFactor[] = [];
    for (const { rule, score } of RULES) {
      if (rule.evaluate(context, config)) {
        const contribution = score(config);
        factors.push({
          code: rule.code,
          description: rule.description,
          score: contribution,
          scoreContribution: contribution,
        });
      }
    }
    const total = factors.reduce((sum, factor) => sum + factorScore(factor), 0);
    const riskScore = Math.max(0, Math.min(100, total));
    return {
      riskScore,
      riskLevel: riskLevelFromScore(riskScore),
      decision: decisionFromScore(riskScore, config),
      factors,
    };
  }

  /** Human-readable trail stored on alerts, e.g. "Decision REVIEW with score 40: A, B". */
  reasonFor(outcome: FraudOutcome): string {
    const codes = outcome.factors.map((f) => f.code).join(', ');
    return `Decision ${outcome.decision} with score ${outcome.riskScore}${codes ? `: ${codes}` : ''}`;
  }

  /** Exposed for the demo UI so the rule set stays inspectable. */
  describeRules(): Array<{ code: string; description: string; score: number }> {
    return RULES.map(({ rule, score }) => ({
      code: rule.code,
      description: rule.description,
      score: score(this.config),
    }));
  }
}
