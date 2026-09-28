package com.nexabank.fraud;

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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6 fraud integration: evaluation before balance mutation, atomicity,
 * alert creation, review workflow and /fraud/** authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FraudIntegrationTest {

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

    @Autowired
    private FraudEvaluationRepository evaluations;

    @Autowired
    private FraudAlertRepository alerts;

    private String employeeAuth;
    private String fraudAuth;
    private String adminAuth;

    @BeforeEach
    void authenticate() {
        employeeAuth = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
        fraudAuth = AuthTestHelper.bearer(
                AuthTestHelper.fraudToken(users, passwordEncoder, jwtService));
        adminAuth = AuthTestHelper.bearer(
                AuthTestHelper.adminToken(users, passwordEncoder, jwtService));
    }

    // ---------- APPROVE ----------

    @Test
    void approvedDepositCompletesAndPersistsEvaluation() throws Exception {
        long accountId = freshAccount();
        long evalsBefore = evaluations.count();

        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("1000.00", "normal", key())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        assertThat(balanceOf(accountId)).isEqualByComparingTo("1000.00");
        assertThat(evaluations.count()).isEqualTo(evalsBefore + 1);
        // No alert for APPROVE.
        mockMvc.perform(get("/api/v1/fraud/alerts?page=0&size=100")
                        .header("Authorization", fraudAuth))
                .andExpect(status().isOk());
    }

    // ---------- REVIEW ----------

    @Test
    void reviewTransferHoldsBalancesAndCreatesAlert() throws Exception {
        // Setup: source funded well below the large threshold per deposit.
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "40000.00");
        fund(source, "40000.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "fresh");
        BigDecimal sourceBefore = balanceOf(source);
        BigDecimal destBefore = balanceOf(dest);
        long alertsBefore = alerts.count();

        // Large (25) + new beneficiary (15) = 40 → REVIEW.
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "60000.00", "big gift", key())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message",
                        is("Your transaction could not be completed at this time.")));

        // Customer-safe: no score, rules or factors leak.
        MvcResult blocked = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "60000.00", "big gift", key())))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        String body = blocked.getResponse().getContentAsString();
        assertThat(body).doesNotContain("riskScore");
        assertThat(body).doesNotContain("LARGE_TRANSACTION");
        assertThat(body).doesNotContain("NEW_BENEFICIARY");

        // No balance mutation, no completed legs.
        assertThat(balanceOf(source)).isEqualByComparingTo(sourceBefore.toPlainString());
        assertThat(balanceOf(dest)).isEqualByComparingTo(destBefore.toPlainString());
        assertThat(totalElements(history(source, annAuth))).isEqualTo(2);
        // Fraud trail survived the rollback: evaluation + OPEN alert.
        assertThat(alerts.count()).isGreaterThanOrEqualTo(alertsBefore + 1);
        MvcResult queue = mockMvc.perform(get("/api/v1/fraud/alerts?status=OPEN&page=0&size=50")
                        .header("Authorization", fraudAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status", is("OPEN")))
                .andReturn();
        assertThat(queue.getResponse().getContentAsString()).contains("REVIEW");
    }

    // ---------- BLOCK ----------

    @Test
    void blockedDepositHoldsBalanceAndCreatesAlert() throws Exception {
        long accountId = freshAccount();
        // Six small deposits build frequency history without tripping rules.
        for (int i = 0; i < 6; i++) {
            deposit(accountId, "100.00", employeeAuth);
        }
        BigDecimal before = balanceOf(accountId);
        long alertsBefore = alerts.count();

        // High-frequency (20) + large (25) + spike (20) = 65 → BLOCK.
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("60000.00", "suspicious", key())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message",
                        is("Your transaction could not be completed at this time.")));

        assertThat(balanceOf(accountId)).isEqualByComparingTo(before.toPlainString());
        assertThat(totalElements(history(accountId, employeeAuth))).isEqualTo(6);
        assertThat(alerts.count()).isGreaterThanOrEqualTo(alertsBefore + 1);
    }

    @Test
    void blockedTransferDoesNotPartiallySettle() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "40000.00");
        fund(source, "40000.00");
        fund(source, "40000.00");
        fund(source, "40000.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "fresh");
        // Two small transfers build rapid-transfer history (each APPROVE: +15 only).
        transfer(source, beneficiary, "100.00", annAuth);
        transfer(source, beneficiary, "100.00", annAuth);
        BigDecimal sourceBefore = balanceOf(source);
        BigDecimal destBefore = balanceOf(dest);

        // Large (25) + new beneficiary (15) + rapid (20) + high-frequency (20) = 80 → BLOCK.
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "60000.00", "attack", key())))
                .andExpect(status().isUnprocessableEntity());

        // Atomicity: neither side moved.
        assertThat(balanceOf(source)).isEqualByComparingTo(sourceBefore.toPlainString());
        assertThat(balanceOf(dest)).isEqualByComparingTo(destBefore.toPlainString());
    }

    // ---------- evaluation history ----------

    @Test
    void evaluationsAreHistoricalAndExplainable() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "100.00", employeeAuth);
        deposit(accountId, "200.00", employeeAuth);

        MvcResult result = mockMvc.perform(get("/api/v1/fraud/evaluations?page=0&size=50")
                        .header("Authorization", fraudAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].evaluationVersion", is("v1")))
                .andExpect(jsonPath("$.content[0].factors").isArray())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("APPROVE");
    }

    // ---------- alert workflow ----------

    @Test
    void alertWorkflowEnforcesValidTransitions() throws Exception {
        long alertId = openAlertId();

        // OPEN → UNDER_REVIEW.
        mockMvc.perform(post("/api/v1/fraud/alerts/" + alertId + "/review")
                        .header("Authorization", fraudAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UNDER_REVIEW")))
                .andExpect(jsonPath("$.reviewedBy").isString());

        // Re-taking review is rejected.
        mockMvc.perform(post("/api/v1/fraud/alerts/" + alertId + "/review")
                        .header("Authorization", fraudAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // UNDER_REVIEW → RESOLVED with analyst + timestamp recorded.
        mockMvc.perform(post("/api/v1/fraud/alerts/" + alertId + "/resolve")
                        .header("Authorization", fraudAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"confirmed legitimate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")))
                .andExpect(jsonPath("$.reviewedAt").isString());

        // Terminal state accepts no further transitions.
        mockMvc.perform(post("/api/v1/fraud/alerts/" + alertId + "/false-positive")
                        .header("Authorization", fraudAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alertCanBeMarkedFalsePositive() throws Exception {
        long alertId = openAlertId();

        mockMvc.perform(post("/api/v1/fraud/alerts/" + alertId + "/review")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/fraud/alerts/" + alertId + "/false-positive")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"expected customer behavior\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FALSE_POSITIVE")));
    }

    @Test
    void directResolveFromOpenIsRejected() throws Exception {
        long alertId = openAlertId();
        mockMvc.perform(post("/api/v1/fraud/alerts/" + alertId + "/resolve")
                        .header("Authorization", fraudAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- authorization ----------

    @Test
    void fraudEndpointsRequireAnalystOrAdmin() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        String customerAuth = customerAuth(ann);

        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .header("Authorization", customerAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .header("Authorization", employeeAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .header("Authorization", fraudAuth))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fraud/alerts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/fraud/dashboard")
                        .header("Authorization", customerAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fraud/dashboard")
                        .header("Authorization", fraudAuth))
                .andExpect(status().isOk());
    }

    @Test
    void alertDetailIsExplainable() throws Exception {
        long alertId = openAlertId();
        mockMvc.perform(get("/api/v1/fraud/alerts/" + alertId)
                        .header("Authorization", fraudAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskScore").isNumber())
                .andExpect(jsonPath("$.decision").isString())
                .andExpect(jsonPath("$.factors").isArray())
                .andExpect(jsonPath("$.evaluationVersion", is("v1")))
                .andExpect(jsonPath("$.status", is("OPEN")));
    }

    // ---------- helpers ----------

    private long openAlertId() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "40000.00");
        fund(source, "40000.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "fresh");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "60000.00", "flag me", key())))
                .andExpect(status().isUnprocessableEntity());
        MvcResult queue = mockMvc.perform(get("/api/v1/fraud/alerts?status=OPEN&page=0&size=1")
                        .header("Authorization", fraudAuth))
                .andExpect(status().isOk())
                .andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(
                queue.getResponse().getContentAsString(), "$.content[0].id");
        return id.longValue();
    }

    private String uniqueEmail() {
        return "fraud-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private String key() {
        return "fraud-idem-" + UUID.randomUUID();
    }

    private String customerAuth(Customer customer) {
        return AuthTestHelper.bearer(
                AuthTestHelper.customerToken(users, passwordEncoder, jwtService, customer));
    }

    private long freshAccount() throws Exception {
        return accountOf(entityOf(createCustomer(uniqueEmail())).getId());
    }

    private long createCustomer(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Fraud","lastName":"Tester","email":"%s","phone":null}"""
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

    private void fund(long accountId, String amount) throws Exception {
        deposit(accountId, amount, employeeAuth);
    }

    private MvcResult deposit(long accountId, String amount, String auth) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody(amount, "test deposit", key())))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String depositBody(String amount, String description, String idemKey) {
        return """
                {"amount":%s,"description":"%s","idempotencyKey":"%s"}"""
                .formatted(amount, description, idemKey);
    }

    private long beneficiaryIdOf(String auth, long accountId, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%d,"nickname":"%s"}""".formatted(accountId, nickname)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String transferBody(long sourceId, long beneficiaryId, String amount, String description, String idemKey) {
        return """
                {"sourceAccountId":%d,"beneficiaryId":%d,"amount":%s,"description":"%s","idempotencyKey":"%s"}"""
                .formatted(sourceId, beneficiaryId, amount, description, idemKey);
    }

    private MvcResult transfer(long sourceId, long beneficiaryId, String amount, String auth) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(sourceId, beneficiaryId, amount, "test", key())))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private BigDecimal balanceOf(long accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", employeeAuth))
                .andExpect(status().isOk())
                .andReturn();
        Object value = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.balance");
        return new BigDecimal(String.valueOf(value));
    }

    private MvcResult history(long accountId, String auth) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/" + accountId + "/transactions?page=0&size=100")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
    }

    private int totalElements(MvcResult page) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                page.getResponse().getContentAsString(), "$.totalElements");
    }
}
