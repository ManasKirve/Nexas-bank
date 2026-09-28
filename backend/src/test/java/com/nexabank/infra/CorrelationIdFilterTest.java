package com.nexabank.infra;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 correlation-id contract: generated when missing, propagated when
 * valid, replaced when invalid, echoed on responses/errors, and never
 * leaking MDC state between requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingCorrelationIdIsGeneratedAndEchoed() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andReturn();
        String generated = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(generated).isNotBlank();
        // Generated ids are UUIDs.
        assertThat(generated).matches("[0-9a-fA-F-]{36}");
    }

    @Test
    void validCorrelationIdIsPropagated() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header(CorrelationIdFilter.HEADER, "test-trace-123_ABC"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "test-trace-123_ABC"));
    }

    @Test
    void invalidCorrelationIdIsReplaced() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/health")
                        .header(CorrelationIdFilter.HEADER,
                                "way-too-long-".repeat(20) + "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andReturn();
        String echoed = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(echoed).doesNotContain("<script>");
        assertThat(echoed).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void errorResponsesCarryTheCorrelationId() throws Exception {
        // Unknown account → 404 ApiError must echo the sent correlation id.
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/999999")
                        .header(CorrelationIdFilter.HEADER, "trace-404-check")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "trace-404-check"))
                .andReturn();
        // 401 (bad token) still echoes the header.
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void mdcIsCleanedUpAfterRequests() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header(CorrelationIdFilter.HEADER, "cleanup-check"))
                .andExpect(status().isOk());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void normalizeRules() {
        assertThat(CorrelationIdFilter.normalize(null)).matches("[0-9a-fA-F-]{36}");
        assertThat(CorrelationIdFilter.normalize("   ")).matches("[0-9a-fA-F-]{36}");
        assertThat(CorrelationIdFilter.normalize("ok-123_ABC")).isEqualTo("ok-123_ABC");
        assertThat(CorrelationIdFilter.normalize("  ok-123  ")).isEqualTo("ok-123");
        assertThat(CorrelationIdFilter.normalize("x".repeat(65))).hasSize(36);
        assertThat(CorrelationIdFilter.normalize("a b")).hasSize(36);
    }

    @Test
    void currentFallsBackOutsideRequests() {
        MDC.clear();
        assertThat(CorrelationIdFilter.current()).isEqualTo("none");
    }
}
