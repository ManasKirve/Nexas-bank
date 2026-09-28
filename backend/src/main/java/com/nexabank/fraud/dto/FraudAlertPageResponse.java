package com.nexabank.fraud.dto;

import java.util.List;

/** Paginated analyst alert queue. */
public record FraudAlertPageResponse(
        List<FraudAlertResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
