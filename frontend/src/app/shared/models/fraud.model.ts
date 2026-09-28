/** Phase 6 fraud analyst models (analyst-only projections — never shown to customers). */

export type FraudDecision = 'APPROVE' | 'REVIEW' | 'BLOCK';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type FraudAlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type FraudAlertStatus = 'OPEN' | 'UNDER_REVIEW' | 'RESOLVED' | 'FALSE_POSITIVE';

export interface RiskFactor {
  code: string;
  description: string;
  score: number;
  /** Backend serializes contributions as `scoreContribution`; accept both. */
  scoreContribution?: number;
}

export interface FraudAlert {
  id: number;
  severity: FraudAlertSeverity;
  status: FraudAlertStatus;
  reason: string | null;
  createdAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
  accountId: number;
  accountNumber: string;
  transactionId: number | null;
  evaluationId: number;
  riskScore: number;
  decision: FraudDecision;
  riskLevel: RiskLevel;
  amount: number;
  currency: string;
  attemptedReference: string | null;
  transferReference: string | null;
  evaluationVersion: string;
  factors: RiskFactor[];
}

export interface FraudAlertPage {
  content: FraudAlert[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FraudEvaluation {
  id: number;
  accountId: number;
  accountNumber: string;
  transactionId: number | null;
  attemptedReference: string | null;
  transferReference: string | null;
  transactionType: string;
  amount: number;
  currency: string;
  riskScore: number;
  riskLevel: RiskLevel;
  decision: FraudDecision;
  factors: RiskFactor[];
  evaluationVersion: string;
  evaluationReason: string | null;
  evaluatedBy: string | null;
  evaluatedAt: string;
}

export interface FraudEvaluationPage {
  content: FraudEvaluation[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FraudDashboard {
  openAlerts: number;
  underReviewAlerts: number;
  resolvedAlerts: number;
  falsePositiveAlerts: number;
  highRiskAlerts: number;
  blockedEvaluations: number;
  reviewEvaluations: number;
  recentAlerts: FraudAlert[];
}

/** Normalizes either factor shape to its numeric contribution. */
export function factorScore(factor: RiskFactor): number {
  return factor.scoreContribution ?? factor.score ?? 0;
}
