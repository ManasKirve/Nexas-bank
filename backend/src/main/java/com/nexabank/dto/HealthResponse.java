package com.nexabank.dto;

import java.time.Instant;

/**
 * DTO for the connectivity health check. Entities are never exposed directly;
 * all REST responses use DTOs.
 */
public record HealthResponse(String status, String service, Instant timestamp) {
}
