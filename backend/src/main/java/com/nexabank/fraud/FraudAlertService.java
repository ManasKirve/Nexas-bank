package com.nexabank.fraud;

import com.nexabank.exception.InvalidStateTransitionException;
import com.nexabank.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Analyst review workflow. All transitions are server-side validated:
 * OPEN → UNDER_REVIEW → RESOLVED / FALSE_POSITIVE. Every action records the
 * analyst username and timestamp.
 */
@Service
public class FraudAlertService {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertService.class);

    private final FraudAlertRepository alerts;

    public FraudAlertService(FraudAlertRepository alerts) {
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public Page<FraudAlert> list(FraudAlertStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), bounded(size));
        if (status == null) {
            return alerts.findAllByOrderByCreatedAtDescIdDesc(pageable);
        }
        return alerts.findByStatusOrderByCreatedAtDescIdDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public FraudAlert require(Long id) {
        return alerts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fraud alert not found with id " + id));
    }

    @Transactional
    public FraudAlert markUnderReview(Long id, String analystUsername) {
        FraudAlert alert = require(id);
        if (alert.getStatus() != FraudAlertStatus.OPEN) {
            throw new InvalidStateTransitionException(
                    "Alert " + id + " must be OPEN to start review (current: " + alert.getStatus() + ")");
        }
        alert.setStatus(FraudAlertStatus.UNDER_REVIEW);
        alert.setReviewedBy(analystUsername);
        alert.setReviewedAt(Instant.now());
        log.info("Fraud alert {} taken UNDER_REVIEW by {}", id, analystUsername);
        return alert;
    }

    @Transactional
    public FraudAlert resolve(Long id, String analystUsername, String reason) {
        FraudAlert alert = require(id);
        if (alert.getStatus() != FraudAlertStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException(
                    "Alert " + id + " must be UNDER_REVIEW to resolve (current: " + alert.getStatus() + ")");
        }
        alert.setStatus(FraudAlertStatus.RESOLVED);
        alert.setReviewedBy(analystUsername);
        alert.setReviewedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            alert.setReason(reason.trim());
        }
        log.info("Fraud alert {} RESOLVED by {}", id, analystUsername);
        return alert;
    }

    @Transactional
    public FraudAlert markFalsePositive(Long id, String analystUsername, String reason) {
        FraudAlert alert = require(id);
        if (alert.getStatus() != FraudAlertStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException(
                    "Alert " + id + " must be UNDER_REVIEW to mark false-positive (current: " + alert.getStatus() + ")");
        }
        alert.setStatus(FraudAlertStatus.FALSE_POSITIVE);
        alert.setReviewedBy(analystUsername);
        alert.setReviewedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            alert.setReason(reason.trim());
        }
        log.info("Fraud alert {} marked FALSE_POSITIVE by {}", id, analystUsername);
        return alert;
    }

    private int bounded(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
