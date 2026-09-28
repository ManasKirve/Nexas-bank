package com.nexabank.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API contract for /api/v1/accounts and the nested customer-accounts resource.
 * Account APIs require authentication; management operations additionally
 * require BANK_EMPLOYEE/ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String authHeader;

    @BeforeEach
    void authenticate() {
        authHeader = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
    }

    @Test
    void createReturns201WithZeroBalance() throws Exception {
        long customerId = createCustomer("acct-owner@example.com");

        MvcResult first = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"accountType":"SAVINGS","currency":"INR"}""".formatted(customerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber", matchesPattern("\\d{12}")))
                .andExpect(jsonPath("$.balance", is(0)))
                .andExpect(jsonPath("$.currency", is("INR")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andReturn();

        // Numbers are sequential within one owner's flow (exact seed varies
        // because controller tests share one database).
        String firstNumber = com.jayway.jsonpath.JsonPath.read(
                first.getResponse().getContentAsString(), "$.accountNumber");
        MvcResult second = createAccount(customerId, "CHECKING");
        String secondNumber = com.jayway.jsonpath.JsonPath.read(
                second.getResponse().getContentAsString(), "$.accountNumber");
        assertThat(Long.parseLong(secondNumber)).isEqualTo(Long.parseLong(firstNumber) + 1);
    }

    @Test
    void createForMissingCustomerReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":999999,"accountType":"SAVINGS","currency":"INR"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Customer not found")));
    }

    @Test
    void customerAccountsListsOnlyOwnedAccounts() throws Exception {
        long ann = createCustomer("ann-accts@example.com");
        long bob = createCustomer("bob-accts@example.com");
        createAccount(ann, "SAVINGS");
        createAccount(ann, "CHECKING");
        createAccount(bob, "SAVINGS");

        mockMvc.perform(get("/api/v1/customers/" + ann + "/accounts")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/customers/" + bob + "/accounts")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Global listing shares one test database across test methods,
        // so only assert it contains at least the three just created.
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(3)));
    }

    @Test
    void statusTransitionsAreEnforced() throws Exception {
        long customerId = createCustomer("status-flow@example.com");
        long accountId = accountIdOf(createAccount(customerId, "SAVINGS"));

        mockMvc.perform(put("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FROZEN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FROZEN")));

        mockMvc.perform(put("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CLOSED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")));

        // CLOSED is terminal: reactivation must fail with 400.
        mockMvc.perform(put("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot transition")));
    }

    @Test
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer this.is.not.a.valid.token"))
                .andExpect(status().isUnauthorized());
    }

    private long createCustomer(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test","lastName":"User","email":"%s","phone":null}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MvcResult createAccount(long customerId, String type) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"accountType":"%s","currency":"INR"}"""
                                .formatted(customerId, type)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private long accountIdOf(MvcResult result) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
