package com.nexabank.fraud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Historical fraud evaluation persistence. Evaluations are append-only —
 * never updated or deleted by normal operations.
 */
public interface FraudEvaluationRepository extends JpaRepository<FraudEvaluation, Long> {

    List<FraudEvaluation> findByAccountIdOrderByEvaluatedAtDescIdDesc(Long accountId);

    Page<FraudEvaluation> findByAccountIdOrderByEvaluatedAtDescIdDesc(Long accountId, Pageable pageable);

    Page<FraudEvaluation> findAllByOrderByEvaluatedAtDescIdDesc(Pageable pageable);

    Page<FraudEvaluation> findByDecisionOrderByEvaluatedAtDescIdDesc(FraudDecision decision, Pageable pageable);

    long countByDecision(FraudDecision decision);
}
