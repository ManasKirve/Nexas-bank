package com.nexabank.controller;

import com.nexabank.dto.HealthResponse;
import com.nexabank.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connectivity probe used by the Angular frontend and smoke tests.
 * Real banking endpoints will be added in later phases under /api/v1/...
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(healthService.currentHealth());
    }
}
