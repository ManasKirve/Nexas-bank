package com.nexabank.infra;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 rate-limit contract: fixed-window allow/deny, longest-prefix
 * rule match, per-client key isolation, and the 429 + Retry-After +
 * ApiError response shape.
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.rules[0].path-prefix=/api/v1/health",
        "app.rate-limit.rules[0].max-requests=2",
        "app.rate-limit.rules[0].window=1m"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private InMemoryRateLimitStore store;

    @Test
    void allowsUpToLimitThenReturns429WithRetryAfter() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.correlationId").isString());
    }

    @Test
    void unmatchedPathsAreNotLimited() {
        // /actuator/health is outside /api/** (filter) and has no rule.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        for (int i = 0; i < 10; i++) {
            rateLimitService.check(request);
        }
    }

    @Test
    void longestPrefixRuleWins() {
        RateLimitService.Rule rule = rateLimitService.match("/api/v1/health/detail");
        assertThat(rule).isNotNull();
        assertThat(rule.maxRequests()).isEqualTo(2);
        assertThat(rateLimitService.match("/api/v1/accounts/3")).isNull();
    }

    @Test
    void quotaIsPerClientKey() {
        String window = "quota-isolation-" + System.nanoTime();
        RateLimitStore.Acquisition first =
                store.tryAcquire(window + ":a", 1, Duration.ofMinutes(1));
        RateLimitStore.Acquisition second =
                store.tryAcquire(window + ":a", 1, Duration.ofMinutes(1));
        RateLimitStore.Acquisition other =
                store.tryAcquire(window + ":b", 1, Duration.ofMinutes(1));
        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isFalse();
        assertThat(second.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
        assertThat(other.allowed()).isTrue();
    }

    @Test
    void windowResetAllowsAgain() throws InterruptedException {
        String key = "window-reset-" + System.nanoTime();
        assertThat(store.tryAcquire(key, 1, Duration.ofMillis(50)).allowed()).isTrue();
        assertThat(store.tryAcquire(key, 1, Duration.ofMillis(50)).allowed()).isFalse();
        Thread.sleep(80);
        assertThat(store.tryAcquire(key, 1, Duration.ofMillis(50)).allowed()).isTrue();
    }

    @Test
    void checkThrowsWithRetryAfter() {
        // Fresh rule quota in this context: two passes, third throws.
        // (The MockMvc test above shares this store, so tolerate pre-use.)
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        int allowed = 0;
        RateLimitExceededException denied = null;
        for (int i = 0; i < 5 && denied == null; i++) {
            try {
                rateLimitService.check(request);
                allowed++;
            } catch (RateLimitExceededException ex) {
                denied = ex;
            }
        }
        assertThat(denied).isNotNull();
        assertThat(denied.getRetryAfterSeconds()).isGreaterThanOrEqualTo(1);
        assertThat(allowed).isLessThanOrEqualTo(2);
    }
}
