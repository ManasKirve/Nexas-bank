package com.nexabank.fraud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Fraud alert persistence for the analyst review queue.
 */
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    Page<FraudAlert> findByStatusOrderByCreatedAtDescIdDesc(FraudAlertStatus status, Pageable pageable);

    Page<FraudAlert> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<FraudAlert> findByStatusOrderByCreatedAtDescIdDesc(FraudAlertStatus status);

    long countByStatus(FraudAlertStatus status);

    long countBySeverityAndStatus(FraudAlertSeverity severity, FraudAlertStatus status);

    Optional<FraudAlert> findByFraudEvaluationId(Long evaluationId);
}
