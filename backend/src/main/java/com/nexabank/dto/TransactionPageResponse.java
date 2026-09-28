package com.nexabank.dto;

import java.util.List;

/**
 * Paginated transaction history payload (newest first).
 */
public record TransactionPageResponse(
        List<TransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
