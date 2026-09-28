package com.nexabank.fraud.dto;

import java.util.List;

/** Paginated historical evaluation list for analysts. */
public record FraudEvaluationPageResponse(
        List<FraudEvaluationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
