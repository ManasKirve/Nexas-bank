package com.nexabank.dto;

/**
 * Lightweight aggregate counts for the dashboard. Every number comes
 * from the database — the UI never hardcodes banking figures.
 */
public record DashboardSummaryResponse(
        long totalCustomers,
        long totalAccounts,
        long activeAccounts,
        long frozenAccounts
) {
}
