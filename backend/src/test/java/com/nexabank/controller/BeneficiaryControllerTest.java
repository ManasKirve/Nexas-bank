package com.nexabank.controller;

import com.nexabank.entity.Customer;
import com.nexabank.repository.CustomerRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 beneficiary contract: customer-scoped creation, listing,
 * retrieval, soft-disable and ownership enforcement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BeneficiaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String employeeAuth;
    private String fraudAuth;

    @BeforeEach
    void authenticate() {
        employeeAuth = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
        fraudAuth = AuthTestHelper.bearer(
                AuthTestHelper.fraudToken(users, passwordEncoder, jwtService));
    }

    @Test
    void createBeneficiaryLinksDestinationAccount() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long dest = accountOf(createCustomer(uniqueEmail()));
        String annAuth = customerAuth(ann);

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%d,"nickname":"My Savings"}""".formatted(dest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId", is(ann.getId().intValue())))
                .andExpect(jsonPath("$.beneficiaryAccountId", is((int) dest)))
                .andExpect(jsonPath("$.beneficiaryAccountNumber").isString())
                .andExpect(jsonPath("$.nickname", is("My Savings")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void createDefaultsNicknameToAccountNumber() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long dest = accountOf(createCustomer(uniqueEmail()));

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", customerAuth(ann))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%d}""".formatted(dest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").isString());
    }

    @Test
    void createRejectsMissingAndInactiveAccounts() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        String annAuth = customerAuth(ann);

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":999999,"nickname":"ghost"}"""))
                .andExpect(status().isNotFound());

        long frozen = accountOf(createCustomer(uniqueEmail()));
        freeze(frozen);
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%d,"nickname":"frozen"}""".formatted(frozen)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duplicateBeneficiaryIsRejectedButReactivatesAfterDisable() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long dest = accountOf(createCustomer(uniqueEmail()));
        String annAuth = customerAuth(ann);
        long id = beneficiaryIdOf(annAuth, dest, "savings");

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%d,"nickname":"again"}""".formatted(dest)))
                .andExpect(status().isConflict());

        // Soft disable keeps the row; re-adding re-activates the same row.
        mockMvc.perform(delete("/api/v1/beneficiaries/" + id)
                        .header("Authorization", annAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DISABLED")));

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%d,"nickname":"back"}""".formatted(dest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is((int) id)))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.nickname", is("back")));
    }

    @Test
    void listAndGetAreCustomerScoped() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        Customer bob = entityOf(createCustomer(uniqueEmail()));
        long destA = accountOf(createCustomer(uniqueEmail()));
        long destB = accountOf(createCustomer(uniqueEmail()));
        String annAuth = customerAuth(ann);
        String bobAuth = customerAuth(bob);
        long annBen = beneficiaryIdOf(annAuth, destA, "a");
        beneficiaryIdOf(bobAuth, destB, "b");

        // Each customer sees only their own beneficiary.
        mockMvc.perform(get("/api/v1/beneficiaries").header("Authorization", annAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is((int) annBen)));

        // Cross-customer read and disable are forbidden, not leaked.
        mockMvc.perform(get("/api/v1/beneficiaries/" + annBen).header("Authorization", bobAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/beneficiaries/" + annBen).header("Authorization", bobAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/beneficiaries/999999").header("Authorization", annAuth))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffCanListButFraudAnalystIsDenied() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long dest = accountOf(createCustomer(uniqueEmail()));
        beneficiaryIdOf(customerAuth(ann), dest, "a");

        mockMvc.perform(get("/api/v1/beneficiaries").header("Authorization", employeeAuth))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/beneficiaries").header("Authorization", fraudAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", fraudAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%d}""".formatted(dest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void beneficiaryEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/beneficiaries"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":1}"""))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String uniqueEmail() {
        return "ben-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private String customerAuth(Customer customer) {
        return AuthTestHelper.bearer(
                AuthTestHelper.customerToken(users, passwordEncoder, jwtService, customer));
    }

    private long createCustomer(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ben","lastName":"Owner","email":"%s","phone":null}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Customer entityOf(long customerId) {
        return customers.findById(customerId).orElseThrow();
    }

    private long accountOf(long customerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"accountType":"SAVINGS","currency":"INR"}"""
                                .formatted(customerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void freeze(long accountId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FROZEN"}"""))
                .andExpect(status().isOk());
    }

    private long beneficiaryIdOf(String auth, long accountId, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%d,"nickname":"%s"}""".formatted(accountId, nickname)))
                .andExpect(status().isCreated())
                .andReturn();
        Object id = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id");
        return ((Number) id).longValue();
    }
}
