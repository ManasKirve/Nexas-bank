package com.nexabank.fraud;

import com.nexabank.fraud.dto.FraudAlertPageResponse;
import com.nexabank.fraud.dto.FraudAlertResponse;
import com.nexabank.fraud.dto.FraudDashboardResponse;
import com.nexabank.fraud.dto.FraudEvaluationPageResponse;
import com.nexabank.fraud.dto.FraudEvaluationResponse;
import com.nexabank.fraud.dto.ReviewAlertRequest;
import com.nexabank.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fraud analyst API. Thin adapter: role gates + service delegation.
 *
 * <p>Access: FRAUD_ANALYST and ADMIN only (also enforced in
 * {@code SecurityConfig} at the URL level — CUSTOMER and BANK_EMPLOYEE get
 * 403). Evaluations and alerts are never exposed to customers.</p>
 */
@RestController
@RequestMapping("/api/v1/fraud")
@PreAuthorize("hasAnyRole('FRAUD_ANALYST','ADMIN')")
public class FraudAlertController {

    private final FraudAlertService alertService;
    private final FraudEvaluationService evaluationService;
    private final FraudAlertRepository alertRepository;
    private final FraudEvaluationRepository evaluationRepository;
    private final CurrentUserService currentUserService;

    public FraudAlertController(
            FraudAlertService alertService,
            FraudEvaluationService evaluationService,
            FraudAlertRepository alertRepository,
            FraudEvaluationRepository evaluationRepository,
            CurrentUserService currentUserService) {
        this.alertService = alertService;
        this.evaluationService = evaluationService;
        this.alertRepository = alertRepository;
        this.evaluationRepository = evaluationRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/alerts")
    @Transactional(readOnly = true)
    public ResponseEntity<FraudAlertPageResponse> alerts(
            @RequestParam(required = false) FraudAlertStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FraudAlert> result = alertService.list(status, page, size);
        List<FraudAlertResponse> content = result.getContent().stream()
                .map(alert -> FraudAlertResponse.from(
                        alert, evaluationService.parseFactors(alert.getFraudEvaluation().getFactorsJson())))
                .toList();
        return ResponseEntity.ok(new FraudAlertPageResponse(
                content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/alerts/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<FraudAlertResponse> alert(@PathVariable Long id) {
        FraudAlert alert = alertService.require(id);
        return ResponseEntity.ok(FraudAlertResponse.from(
                alert, evaluationService.parseFactors(alert.getFraudEvaluation().getFactorsJson())));
    }

    @PostMapping("/alerts/{id}/review")
    @Transactional
    public ResponseEntity<FraudAlertResponse> review(
            @PathVariable Long id, @Valid @RequestBody(required = false) ReviewAlertRequest request) {
        String analyst = currentUserService.requireUser().getUsername();
        FraudAlert alert = alertService.markUnderReview(id, analyst);
        return ResponseEntity.ok(FraudAlertResponse.from(
                alert, evaluationService.parseFactors(alert.getFraudEvaluation().getFactorsJson())));
    }

    @PostMapping("/alerts/{id}/resolve")
    @Transactional
    public ResponseEntity<FraudAlertResponse> resolve(
            @PathVariable Long id, @Valid @RequestBody(required = false) ReviewAlertRequest request) {
        String analyst = currentUserService.requireUser().getUsername();
        String reason = request == null ? null : request.reason();
        FraudAlert alert = alertService.resolve(id, analyst, reason);
        return ResponseEntity.ok(FraudAlertResponse.from(
                alert, evaluationService.parseFactors(alert.getFraudEvaluation().getFactorsJson())));
    }

    @PostMapping("/alerts/{id}/false-positive")
    @Transactional
    public ResponseEntity<FraudAlertResponse> falsePositive(
            @PathVariable Long id, @Valid @RequestBody(required = false) ReviewAlertRequest request) {
        String analyst = currentUserService.requireUser().getUsername();
        String reason = request == null ? null : request.reason();
        FraudAlert alert = alertService.markFalsePositive(id, analyst, reason);
        return ResponseEntity.ok(FraudAlertResponse.from(
                alert, evaluationService.parseFactors(alert.getFraudEvaluation().getFactorsJson())));
    }

    @GetMapping("/evaluations")
    @Transactional(readOnly = true)
    public ResponseEntity<FraudEvaluationPageResponse> evaluations(
            @RequestParam(required = false) FraudDecision decision,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FraudEvaluation> result;
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        if (decision == null) {
            result = evaluationRepository.findAllByOrderByEvaluatedAtDescIdDesc(pageable);
        } else {
            result = evaluationRepository.findByDecisionOrderByEvaluatedAtDescIdDesc(decision, pageable);
        }
        List<FraudEvaluationResponse> content = result.getContent().stream()
                .map(evaluation -> FraudEvaluationResponse.from(
                        evaluation, evaluationService.parseFactors(evaluation.getFactorsJson())))
                .toList();
        return ResponseEntity.ok(new FraudEvaluationPageResponse(
                content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/evaluations/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<FraudEvaluationResponse> evaluation(@PathVariable Long id) {
        FraudEvaluation evaluation = evaluationRepository.findById(id)
                .orElseThrow(() -> new com.nexabank.exception.ResourceNotFoundException(
                        "Fraud evaluation not found with id " + id));
        return ResponseEntity.ok(FraudEvaluationResponse.from(
                evaluation, evaluationService.parseFactors(evaluation.getFactorsJson())));
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public ResponseEntity<FraudDashboardResponse> dashboard() {
        long open = alertRepository.countByStatus(FraudAlertStatus.OPEN);
        long underReview = alertRepository.countByStatus(FraudAlertStatus.UNDER_REVIEW);
        long resolved = alertRepository.countByStatus(FraudAlertStatus.RESOLVED);
        long falsePositive = alertRepository.countByStatus(FraudAlertStatus.FALSE_POSITIVE);
        long high = alertRepository.countBySeverityAndStatus(FraudAlertSeverity.HIGH, FraudAlertStatus.OPEN)
                + alertRepository.countBySeverityAndStatus(FraudAlertSeverity.CRITICAL, FraudAlertStatus.OPEN);
        long blocked = evaluationRepository.countByDecision(FraudDecision.BLOCK);
        long review = evaluationRepository.countByDecision(FraudDecision.REVIEW);
        List<FraudAlertResponse> recent = alertRepository
                .findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 10)).getContent().stream()
                .map(alert -> FraudAlertResponse.from(
                        alert, evaluationService.parseFactors(alert.getFraudEvaluation().getFactorsJson())))
                .toList();
        return ResponseEntity.ok(new FraudDashboardResponse(
                open, underReview, resolved, falsePositive, high, blocked, review, recent));
    }
}
