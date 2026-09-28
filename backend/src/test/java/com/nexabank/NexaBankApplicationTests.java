package com.nexabank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 1 smoke test: the Spring application context loads.
 * Uses the dedicated "test" profile backed by H2 (see src/test/resources).
 */
@SpringBootTest
@ActiveProfiles("test")
class NexaBankApplicationTests {

    @Test
    void contextLoads() {
    }
}
