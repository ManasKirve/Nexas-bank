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
 * Phase 4 transaction-engine contract: deposits, withdrawals, idempotency,
 * pessimistic-locking concurrency, rollback, history pagination/ownership
 * and money-movement authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerTest {

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
    private String adminAuth;
    private String fraudAuth;

    @BeforeEach
    void authenticate() {
        employeeAuth = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
        adminAuth = AuthTestHelper.bearer(
                AuthTestHelper.adminToken(users, passwordEncoder, jwtService));
        fraudAuth = AuthTestHelper.bearer(
                AuthTestHelper.fraudToken(users, passwordEncoder, jwtService));
    }

    // ---------- deposits ----------

    @Test
    void successfulDepositCreatesCompletedTransaction() throws Exception {
        long accountId = freshAccount();
        String key = key();

        MvcResult result = mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("1000.00", "Cash deposit", key)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference", matchesPattern("TXN-\\d{8}-.+")))
                .andExpect(jsonPath("$.transactionType", is("DEPOSIT")))
                .andExpect(jsonPath("$.currency", is("INR")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn();

        assertThat(amountOf(result, "$.balanceBefore")).isEqualByComparingTo("0");
        assertThat(amountOf(result, "$.balanceAfter")).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("1000.00");
    }

    @Test
    void depositRejectsNonPositiveAmounts() throws Exception {
        long accountId = freshAccount();
        for (String amount : List.of("0", "0.00", "-50.00")) {
            mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                            .header("Authorization", employeeAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(depositBody(amount, "bad", key())))
                    .andExpect(status().isBadRequest());
        }
        assertThat(balanceOf(accountId)).isEqualByComparingTo("0");
    }

    @Test
    void depositToMissingAccountReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/999999/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("100.00", "x", key())))
                .andExpect(status().isNotFound());
    }

    @Test
    void depositToFrozenAccountReturns422() throws Exception {
        long accountId = freshAccount();
        freeze(accountId);

        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("100.00", "x", key())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("not ACTIVE")));
        assertThat(balanceOf(accountId)).isEqualByComparingTo("0");
    }

    @Test
    void adminCanDepositButFraudAnalystCannot() throws Exception {
        long accountId = freshAccount();

        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("10.00", "admin", key())))
                .andExpect(status().isCreated());

        // FRAUD_ANALYST has fraud-monitoring access but no money-moving permission.
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", fraudAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("10.00", "fraud", key())))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotDepositIntoAnotherCustomersAccount() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        Customer bob = entityOf(createCustomer(uniqueEmail()));
        long annAccount = accountOf(bob);
        String annAuth = AuthTestHelper.bearer(
                AuthTestHelper.customerToken(users, passwordEncoder, jwtService, ann));

        mockMvc.perform(post("/api/v1/accounts/" + annAccount + "/deposits")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("100.00", "x", key())))
                .andExpect(status().isForbidden());
        assertThat(balanceOf(annAccount)).isEqualByComparingTo("0");
    }

    @Test
    void successiveDepositsChainBalances() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "500.00", employeeAuth);
        MvcResult second = deposit(accountId, "250.00", employeeAuth);

        assertThat(amountOf(second, "$.balanceBefore")).isEqualByComparingTo("500.00");
        assertThat(amountOf(second, "$.balanceAfter")).isEqualByComparingTo("750.00");
    }

    // ---------- withdrawals ----------

    @Test
    void successfulWithdrawalDebitsBalance() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "1000.00", employeeAuth);

        MvcResult result = mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdrawals")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody("400.00", "ATM withdrawal", key())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType", is("WITHDRAWAL")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn();

        assertThat(amountOf(result, "$.balanceBefore")).isEqualByComparingTo("1000.00");
        assertThat(amountOf(result, "$.balanceAfter")).isEqualByComparingTo("600.00");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("600.00");
    }

    @Test
    void insufficientFundsLeavesBalanceUntouched() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "100.00", employeeAuth);
        int txnsBefore = totalElements(history(accountId, employeeAuth));

        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdrawals")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody("500.00", "too much", key())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Insufficient funds")));

        // No partial commit: same balance, no new ledger row.
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
        assertThat(totalElements(history(accountId, employeeAuth))).isEqualTo(txnsBefore);
    }

    @Test
    void withdrawalRejectsNonPositiveAmounts() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "100.00", employeeAuth);
        for (String amount : List.of("0", "-10.00")) {
            mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdrawals")
                            .header("Authorization", employeeAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(withdrawBody(amount, "bad", key())))
                    .andExpect(status().isBadRequest());
        }
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
    }

    @Test
    void withdrawalOnFrozenAccountReturns422() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "100.00", employeeAuth);
        freeze(accountId);

        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdrawals")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody("10.00", "x", key())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void customerCanOperateOwnAccountButNotAnothers() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long own = accountOf(ann);
        long other = freshAccount();
        String annAuth = AuthTestHelper.bearer(
                AuthTestHelper.customerToken(users, passwordEncoder, jwtService, ann));
        deposit(own, "300.00", employeeAuth);
        deposit(other, "300.00", employeeAuth);

        mockMvc.perform(post("/api/v1/accounts/" + own + "/withdrawals")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody("50.00", "own", key())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/accounts/" + other + "/withdrawals")
                        .header("Authorization", annAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody("50.00", "theirs", key())))
                .andExpect(status().isForbidden());
        assertThat(balanceOf(other)).isEqualByComparingTo("300.00");
    }

    // ---------- idempotency ----------

    @Test
    void repeatedIdempotencyKeyDoesNotMoveMoneyTwice() throws Exception {
        long accountId = freshAccount();
        String body = depositBody("1000.00", "Cash deposit", key());

        MvcResult first = mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String reference = refOf(first);

        // Exact replay returns the original result — balance moves only once.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                            .header("Authorization", employeeAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.transactionReference", is(reference)));
        }
        assertThat(balanceOf(accountId)).isEqualByComparingTo("1000.00");
        assertThat(totalElements(history(accountId, employeeAuth))).isEqualTo(1);
    }

    @Test
    void conflictingIdempotencyReuseIsRejected() throws Exception {
        long accountId = freshAccount();
        String idemKey = key();

        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("100.00", "first", idemKey)))
                .andExpect(status().isCreated());

        // Same key, different amount/type → 409, balance untouched.
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("200.00", "conflict", idemKey)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdrawals")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody("100.00", "conflict", idemKey)))
                .andExpect(status().isConflict());
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
    }

    @Test
    void concurrentDuplicateDepositsMoveMoneyOnce() throws Exception {
        long accountId = freshAccount();
        String body = depositBody("250.00", "race", key());

        List<Integer> statuses = runConcurrently(4, () -> mockMvc.perform(
                        post("/api/v1/accounts/" + accountId + "/deposits")
                                .header("Authorization", employeeAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn().getResponse().getStatus());

        // Winners replay the original row; a pathological loser may see 409 —
        // but never a 5xx and never a second financial movement.
        assertThat(statuses).allSatisfy(s -> assertThat(s).isIn(201, 409));
        assertThat(balanceOf(accountId)).isEqualByComparingTo("250.00");
        assertThat(totalElements(history(accountId, employeeAuth))).isEqualTo(1);
    }

    // ---------- concurrency ----------

    @Test
    void concurrentWithdrawalsSerializeToCorrectBalance() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "1000.00", employeeAuth);

        List<Integer> statuses = runConcurrently(5, () -> mockMvc.perform(
                        post("/api/v1/accounts/" + accountId + "/withdrawals")
                                .header("Authorization", employeeAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(withdrawBody("100.00", "concurrent", key())))
                .andReturn().getResponse().getStatus());

        // Pessimistic row locking serializes the five debits: no lost update.
        assertThat(statuses).containsOnly(201);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("500.00");
        assertThat(totalElements(history(accountId, employeeAuth))).isEqualTo(6);
    }

    // ---------- history ----------

    @Test
    void historyIsPaginatedNewestFirst() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "10.00", employeeAuth);
        deposit(accountId, "20.00", employeeAuth);
        deposit(accountId, "30.00", employeeAuth);

        MvcResult page0 = history(accountId, 0, 2, employeeAuth);
        assertThat(totalElements(page0)).isEqualTo(3);
        List<String> refs0 = refsOf(page0);
        assertThat(refs0).hasSize(2);

        MvcResult page1 = history(accountId, 1, 2, employeeAuth);
        assertThat(refsOf(page1)).hasSize(1);

        // Newest first: page 0 ends where page 1 begins, in creation order.
        MvcResult all = history(accountId, 0, 10, employeeAuth);
        List<String> allRefs = refsOf(all);
        assertThat(allRefs).hasSize(3);
        assertThat(allRefs.get(0)).isEqualTo(refs0.get(0));
        assertThat(allRefs.get(1)).isEqualTo(refs0.get(1));
        assertThat(allRefs.get(2)).isEqualTo(refsOf(page1).get(0));
    }

    @Test
    void customerCannotReadAnotherCustomersHistory() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long other = freshAccount();
        deposit(other, "50.00", employeeAuth);
        String annAuth = AuthTestHelper.bearer(
                AuthTestHelper.customerToken(users, passwordEncoder, jwtService, ann));

        mockMvc.perform(get("/api/v1/accounts/" + other + "/transactions")
                        .header("Authorization", annAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownHistoryReturnsOnlyOwnedAccounts() throws Exception {
        Customer ann = entityOf(createCustomer(uniqueEmail()));
        long mine1 = accountOf(ann);
        long mine2 = accountOf(ann);
        long theirs = freshAccount();
        deposit(mine1, "10.00", employeeAuth);
        deposit(mine2, "20.00", employeeAuth);
        deposit(theirs, "999.00", employeeAuth);
        String annAuth = AuthTestHelper.bearer(
                AuthTestHelper.customerToken(users, passwordEncoder, jwtService, ann));

        MvcResult result = mockMvc.perform(get("/api/v1/accounts/me/transactions?page=0&size=20")
                        .header("Authorization", annAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("999.00");
    }

    @Test
    void employeeCanReadAnyHistoryButFraudAnalystCannot() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "10.00", employeeAuth);

        mockMvc.perform(get("/api/v1/accounts/" + accountId + "/transactions")
                        .header("Authorization", employeeAuth))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts/" + accountId + "/transactions")
                        .header("Authorization", fraudAuth))
                .andExpect(status().isForbidden());
    }

    // ---------- ledger immutability + auth boundary ----------

    @Test
    void ledgerHasNoUpdateOrDeleteEndpoints() throws Exception {
        long accountId = freshAccount();
        deposit(accountId, "10.00", employeeAuth);

        mockMvc.perform(put("/api/v1/accounts/" + accountId + "/transactions/1")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/api/v1/accounts/" + accountId + "/transactions/1")
                        .header("Authorization", employeeAuth))
                .andExpect(status().is4xxClientError());
        assertThat(balanceOf(accountId)).isEqualByComparingTo("10.00");
    }

    @Test
    void transactionEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/1/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody("10.00", "x", key())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/accounts/1/transactions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/accounts/me/transactions")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private long freshAccount() throws Exception {
        return accountOf(entityOf(createCustomer(uniqueEmail())));
    }

    private String uniqueEmail() {
        return "txn-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private String key() {
        return "idem-" + UUID.randomUUID();
    }

    private long createCustomer(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Txn","lastName":"Tester","email":"%s","phone":null}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Customer entityOf(long customerId) {
        return customers.findById(customerId).orElseThrow();
    }

    private long accountOf(Customer customer) throws Exception {
        return accountOf(customer.getId());
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
        mockMvc.perform(put("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FROZEN"}"""))
                .andExpect(status().isOk());
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

    private String withdrawBody(String amount, String description, String idemKey) {
        return """
                {"amount":%s,"description":"%s","idempotencyKey":"%s"}"""
                .formatted(amount, description, idemKey);
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
                result.getResponse().getContentAsString(), "$.transactionReference");
    }

    private MvcResult history(long accountId, String auth) throws Exception {
        return history(accountId, 0, 20, auth);
    }

    private MvcResult history(long accountId, int page, int size, String auth) throws Exception {
        return mockMvc.perform(get(
                        "/api/v1/accounts/" + accountId + "/transactions?page=" + page + "&size=" + size)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
    }

    private int totalElements(MvcResult page) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                pageBody(page), "$.totalElements");
    }

    private List<String> refsOf(MvcResult page) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                pageBody(page), "$.content[*].transactionReference");
    }

    private String pageBody(MvcResult page) throws Exception {
        return page.getResponse().getContentAsString();
    }

    private List<Integer> runConcurrently(int threads, Callable<Integer> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
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
