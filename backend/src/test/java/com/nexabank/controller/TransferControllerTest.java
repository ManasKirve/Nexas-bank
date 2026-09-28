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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 transfer contract: atomic debit+credit, dual ledger legs, ordered
 * locking, idempotency, rollback behavior and money-movement authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferControllerTest {

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

    // ---------- happy path ----------

    @Test
    void successfulTransferSettlesBothSides() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "1000.00");
        fund(dest, "200.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "savings");

        MvcResult result = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "250.00", "rent", key())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferReference", matchesPattern("TRF-\\d{8}-.+")))
                .andExpect(jsonPath("$.sourceAccountId", is((int) source)))
                .andExpect(jsonPath("$.destinationAccountId", is((int) dest)))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.currency", is("INR")))
                .andReturn();

        assertThat(amountOf(result, "$.amount")).isEqualByComparingTo("250.00");
        assertThat(amountOf(result, "$.sourceBalanceBefore")).isEqualByComparingTo("1000.00");
        assertThat(amountOf(result, "$.sourceBalanceAfter")).isEqualByComparingTo("750.00");
        assertThat(amountOf(result, "$.destinationBalanceBefore")).isEqualByComparingTo("200.00");
        assertThat(amountOf(result, "$.destinationBalanceAfter")).isEqualByComparingTo("450.00");
        assertThat(balanceOf(source)).isEqualByComparingTo("750.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("450.00");
    }

    @Test
    void transferWritesTwoLegsWithSharedReference() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "500.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "savings");

        MvcResult result = transfer(source, beneficiary, "100.00", annAuth);
        String ref = refOf(result);

        MvcResult sourceHistory = history(source, annAuth);
        assertThat(typesOf(sourceHistory)).contains("TRANSFER_DEBIT");
        MvcResult destHistory = history(dest, employeeAuth);
        assertThat(typesOf(destHistory)).contains("TRANSFER_CREDIT");

        // Same transfer reference on both sides, with counterparty context.
        String body = sourceHistory.getResponse().getContentAsString();
        assertThat(body).contains(ref);
    }

    // ---------- validation ----------

    @Test
    void transferValidationRejectsBadAmounts() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "500.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "s");

        for (String amount : List.of("0", "0.00", "-10.00")) {
            mockMvc.perform(post("/api/v1/transfers")
                            .header("Authorization", annAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody(source, beneficiary, amount, "x", key())))
                    .andExpect(status().isBadRequest());
        }
        assertThat(balanceOf(source)).isEqualByComparingTo("500.00");
    }

    @Test
    void insufficientFundsRollsBackEntireTransfer() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "100.00");
        fund(dest, "50.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "s");
        int sourceTxns = totalElements(history(source, annAuth));
        int destTxns = totalElements(history(dest, employeeAuth));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "500.00", "too much", key())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Insufficient funds")));

        // Atomicity: neither balance moved, no partial ledger state.
        assertThat(balanceOf(source)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("50.00");
        assertThat(totalElements(history(source, annAuth))).isEqualTo(sourceTxns);
        assertThat(totalElements(history(dest, employeeAuth))).isEqualTo(destTxns);
    }

    @Test
    void inactiveSourceOrDestinationAreRejected() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "500.00");
        fund(dest, "10.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "s");

        freeze(source);
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "10.00", "x", key())))
                .andExpect(status().isUnprocessableEntity());
        unfreeze(source);

        freeze(dest);
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "10.00", "x", key())))
                .andExpect(status().isUnprocessableEntity());
        assertThat(balanceOf(source)).isEqualByComparingTo("500.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("10.00");
    }

    @Test
    void missingAndDisabledBeneficiariesAreRejected() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        fund(source, "500.00");
        String annAuth = customerAuth(ann);

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, 999999L, "10.00", "x", key())))
                .andExpect(status().isNotFound());

        long dest = accountOf(createCustomer(uniqueEmail()));
        long beneficiary = beneficiaryIdOf(annAuth, dest, "s");
        mockMvc.perform(delete("/api/v1/beneficiaries/" + beneficiary)
                        .header("Authorization", annAuth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "10.00", "x", key())))
                .andExpect(status().isUnprocessableEntity());
        assertThat(balanceOf(source)).isEqualByComparingTo("500.00");
    }

    @Test
    void selfTransferIsRejected() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        fund(source, "500.00");
        String annAuth = customerAuth(ann);
        // Beneficiary pointing at the customer's own source account.
        long beneficiary = beneficiaryIdOf(annAuth, source, "self");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "10.00", "x", key())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("different")));
        assertThat(balanceOf(source)).isEqualByComparingTo("500.00");
    }

    // ---------- ownership & roles ----------

    @Test
    void customerCannotUseAnotherCustomersBeneficiaryOrSource() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        Customer bob = entityOf(createCustomer(uniqueEmail()));
        long annSource = accountOf(ann.getId());
        long annDest = accountOf(createCustomer(uniqueEmail()));
        long bobSource = accountOf(bob.getId());
        fund(annSource, "500.00");
        fund(bobSource, "500.00");
        String annAuth = customerAuth(ann);
        String bobAuth = customerAuth(bob);
        long annBeneficiary = beneficiaryIdOf(annAuth, annDest, "a");
        long bobBeneficiary = beneficiaryIdOf(bobAuth, annDest, "b");

        // Bob's source with Ann's beneficiary → 403.
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bobAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(bobSource, annBeneficiary, "10.00", "x", key())))
                .andExpect(status().isForbidden());
        // Ann's source with Bob's beneficiary → 403.
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(annSource, bobBeneficiary, "10.00", "x", key())))
                .andExpect(status().isForbidden());
        // Ann's source directly with Bob's credentials → 403.
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bobAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(annSource, bobBeneficiary, "10.00", "x", key())))
                .andExpect(status().isForbidden());
        assertThat(balanceOf(annSource)).isEqualByComparingTo("500.00");
        assertThat(balanceOf(bobSource)).isEqualByComparingTo("500.00");
    }

    @Test
    void staffCanTransferButFraudAnalystCannot() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "500.00");
        long beneficiary = beneficiaryIdOf(customerAuth(ann), dest, "s");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "50.00", "ops", key())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", fraudAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "50.00", "fraud", key())))
                .andExpect(status().isForbidden());
        assertThat(balanceOf(source)).isEqualByComparingTo("450.00");
    }

    @Test
    void transfersRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccountId":1,"beneficiaryId":1,"amount":10.00,"idempotencyKey":"k"}"""))
                .andExpect(status().isUnauthorized());
    }

    // ---------- idempotency ----------

    @Test
    void repeatedIdempotencyKeyDoesNotTransferTwice() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "1000.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "s");
        String body = transferBody(source, beneficiary, "300.00", "repeat", key());

        MvcResult first = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String ref = refOf(first);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/transfers")
                            .header("Authorization", annAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.transferReference", is(ref)));
        }
        assertThat(balanceOf(source)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("300.00");
        assertThat(totalElements(history(source, annAuth))).isEqualTo(2);
    }

    @Test
    void conflictingIdempotencyReuseIsRejected() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "1000.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "s");
        String idemKey = key();

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "100.00", "first", idemKey)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(source, beneficiary, "200.00", "conflict", idemKey)))
                .andExpect(status().isConflict());
        assertThat(balanceOf(source)).isEqualByComparingTo("900.00");
    }

    @Test
    void concurrentDuplicateTransfersSettleOnce() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "1000.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "s");
        String body = transferBody(source, beneficiary, "150.00", "race", key());

        List<Integer> statuses = runConcurrently(4, () -> mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).allSatisfy(s -> assertThat(s).isIn(201, 409));
        assertThat(balanceOf(source)).isEqualByComparingTo("850.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("150.00");
    }

    // ---------- concurrency ----------

    @Test
    void oppositeDirectionTransfersDoNotDeadlockAndConserveFunds() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        Customer bob = entityOf(createCustomer(uniqueEmail()));
        long accountA = accountOf(ann.getId());
        long accountB = accountOf(bob.getId());
        fund(accountA, "1000.00");
        fund(accountB, "1000.00");
        String annAuth = customerAuth(ann);
        String bobAuth = customerAuth(bob);
        // Cross beneficiaries: Ann pays Bob's account, Bob pays Ann's.
        long benToB = beneficiaryIdOf(annAuth, accountB, "to-b");
        long benToA = beneficiaryIdOf(bobAuth, accountA, "to-a");

        List<Callable<Integer>> tasks = List.of(
                () -> transferStatus(accountA, benToB, "100.00", annAuth),
                () -> transferStatus(accountA, benToB, "100.00", annAuth),
                () -> transferStatus(accountB, benToA, "100.00", bobAuth),
                () -> transferStatus(accountB, benToA, "100.00", bobAuth));

        List<Integer> statuses = runConcurrently(tasks);
        assertThat(statuses).containsOnly(201);

        // Deterministic lock ordering: no deadlock, exact balances, conserved total.
        assertThat(balanceOf(accountA)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(accountB)).isEqualByComparingTo("1000.00");
    }

    @Test
    void concurrentTransfersFromSameSourceSerializeCorrectly() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long source = accountOf(ann.getId());
        long dest = accountOf(createCustomer(uniqueEmail()));
        fund(source, "1000.00");
        String annAuth = customerAuth(ann);
        long beneficiary = beneficiaryIdOf(annAuth, dest, "s");

        List<Callable<Integer>> tasks = List.of(
                () -> transferStatus(source, beneficiary, "100.00", annAuth),
                () -> transferStatus(source, beneficiary, "100.00", annAuth),
                () -> transferStatus(source, beneficiary, "100.00", annAuth));

        assertThat(runConcurrently(tasks)).containsOnly(201);
        assertThat(balanceOf(source)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("300.00");
    }

    // ---------- helpers ----------

    private String uniqueEmail() {
        return "trf-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private String key() {
        return "trf-idem-" + UUID.randomUUID();
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
                                {"firstName":"Trf","lastName":"Tester","email":"%s","phone":null}"""
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
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%s,"description":"setup funding","idempotencyKey":"%s"}"""
                                .formatted(amount, key())))
                .andExpect(status().isCreated());
    }

    private void freeze(long accountId) throws Exception {
        changeStatus(accountId, "FROZEN");
    }

    private void unfreeze(long accountId) throws Exception {
        changeStatus(accountId, "ACTIVE");
    }

    private void changeStatus(long accountId, String status) throws Exception {
        mockMvc.perform(put("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}""".formatted(status)))
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

    private int transferStatus(long sourceId, long beneficiaryId, String amount, String auth) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(sourceId, beneficiaryId, amount, "concurrent", key())))
                .andReturn().getResponse().getStatus();
    }

    private BigDecimal balanceOf(long accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", employeeAuth))
                .andExpect(status().isOk())
                .andReturn();
        return amountOf(result, "$.balance");
    }

    private BigDecimal amountOf(MvcResult result, String path) throws Exception {
        Object value = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), path);
        return new BigDecimal(String.valueOf(value));
    }

    private String refOf(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.transferReference");
    }

    private MvcResult history(long accountId, String auth) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/" + accountId + "/transactions?page=0&size=20")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
    }

    private int totalElements(MvcResult page) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                page.getResponse().getContentAsString(), "$.totalElements");
    }

    private List<String> typesOf(MvcResult page) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                page.getResponse().getContentAsString(), "$.content[*].transactionType");
    }

    private List<Integer> runConcurrently(int threads, Callable<Integer> task) throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(task);
        }
        return runConcurrently(tasks);
    }

    private List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    gate.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            gate.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
