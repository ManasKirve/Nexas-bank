package com.nexabank.infra;

import com.nexabank.repository.UserRepository;
import com.nexabank.security.JwtService;
import com.nexabank.support.AuthTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 observability contract: health aggregates infra state without
 * leaking secrets, business activity increments bounded metrics, and the
 * OpenAPI descriptor is served.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String employeeAuth;

    @BeforeEach
    void authenticate() {
        employeeAuth = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
    }

    @Test
    void healthIsUpWithInfraDetailsAndNoSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header("Authorization", employeeAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // Disabled-but-optional infra reports UNKNOWN, never DOWN here.
        assertThat(body).contains("redis");
        assertThat(body).contains("kafka");
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("localhost:6379");
        assertThat(body).doesNotContain("secret");
    }

    @Test
    void depositIncrementsBoundedMetrics() throws Exception {
        long accountId = freshAccount();
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":25.00,"description":"metrics","idempotencyKey":"%s"}"""
                                .formatted("metrics-" + UUID.randomUUID())))
                .andExpect(status().isCreated());

        // Counter exists with bounded tags only (type/result — no references).
        mockMvc.perform(get("/actuator/metrics/nexabank.transactions")
                        .header("Authorization", employeeAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("nexabank.transactions"))
                .andExpect(jsonPath("$.measurements").isArray());

        // Timer registered for money movement.
        mockMvc.perform(get("/actuator/metrics/nexabank.transaction.duration")
                        .header("Authorization", employeeAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("nexabank.transaction.duration"));
    }

    @Test
    void metricsNeverExposeHighCardinalityTags() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/metrics/nexabank.transactions")
                        .header("Authorization", employeeAuth))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("transactionReference");
        assertThat(body).doesNotContain("accountId");
        assertThat(body).doesNotContain("userId");
    }

    @Test
    void openApiDescriptorIsServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("NexaBank API"));
    }

    private long freshAccount() throws Exception {
        String email = "obs-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult customer = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Obs","lastName":"Test","email":"%s","phone":null}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        long customerId = ((Number) com.jayway.jsonpath.JsonPath.read(
                customer.getResponse().getContentAsString(), "$.id")).longValue();
        MvcResult account = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"accountType":"SAVINGS","currency":"INR"}"""
                                .formatted(customerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                account.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
