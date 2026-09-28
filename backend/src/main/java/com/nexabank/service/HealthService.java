package com.nexabank.service;

import com.nexabank.dto.HealthResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Simple application service for the Phase 1 connectivity check.
 * Keeps business logic out of the controller, per layered-architecture rules.
 */
@Service
public class HealthService {

    public HealthResponse currentHealth() {
        return new HealthResponse("UP", "nexabank-backend", Instant.now());
    }
}
