package com.nexabank.events;

import com.nexabank.infra.InfraProperties;
import com.nexabank.infra.NexaBankMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.nexabank.repository.CustomerRepository;
import com.nexabank.repository.UserRepository;
import com.nexabank.security.JwtService;
import com.nexabank.support.AuthTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 outbox contract: staged in the business transaction, absent on
 * rollback, published + marked by the publisher, retryable on failure and
 * FAILED past the retry budget.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxPublisherTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private InfraProperties infraProperties;

    @Autowired
    private NexaBankMetrics metrics;

    @Autowired
    private UserRepository users;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String employeeAuth;

    @BeforeEach
    void authenticate() {
        employeeAuth = AuthTestHelper.bearer(
                AuthTestHelper.employeeToken(users, passwordEncoder, jwtService));
    }

    @Test
    void completedDepositStagesTransactionEventInSameTransaction() throws Exception {
        long accountId = freshAccount();
        long before = outbox.countByStatus(OutboxStatus.PENDING);

        MvcResult deposit = mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00,"description":"outbox","idempotencyKey":"%s"}"""
                                .formatted("outbox-" + UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        String reference = com.jayway.jsonpath.JsonPath.read(
                deposit.getResponse().getContentAsString(), "$.transactionReference");

        assertThat(outbox.countByStatus(OutboxStatus.PENDING)).isEqualTo(before + 2);
        // One TRANSACTION_COMPLETED (+ one FRAUD_EVALUATION_COMPLETED for the APPROVE).
        List<OutboxEvent> pending = outbox.findByStatusOrderByCreatedAtAscIdAsc(
                OutboxStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 200));
        assertThat(pending.stream()
                .filter(e -> e.getEventType() == OutboxEventType.TRANSACTION_COMPLETED)
                .filter(e -> e.getPayload().contains(reference)))
                .hasSize(1);
    }

    @Test
    void rolledBackBusinessOperationStagesNoEvent() throws Exception {
        long accountId = freshAccount();
        // Seed one small deposit so the account exists with funds.
        deposit(accountId, "50.00");
        long before = outbox.count();

        // Insufficient funds → 422, outer rolls back, nothing staged.
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/withdrawals")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":5000.00,"description":"nope","idempotencyKey":"%s"}"""
                                .formatted("outbox-" + UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());

        assertThat(outbox.count()).isEqualTo(before);
    }

    @Test
    void fraudHeldAttemptStagesFraudEventsButNoLedgerEvent() throws Exception {
        long accountId = freshAccount();
        for (int i = 0; i < 6; i++) {
            deposit(accountId, "100.00");
        }
        // Large deposit → BLOCK (high-frequency + large + spike).
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":60000.00,"description":"held","idempotencyKey":"%s"}"""
                                .formatted("outbox-" + UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());

        List<OutboxEvent> pending = outbox.findByStatusOrderByCreatedAtAscIdAsc(
                OutboxStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(pending.stream()
                .filter(e -> e.getEventType() == OutboxEventType.FRAUD_ALERT_CREATED))
                .isNotEmpty();
        // No ledger event may reference the held attempt's key.
        assertThat(pending.stream()
                .filter(e -> e.getEventType() == OutboxEventType.TRANSACTION_COMPLETED
                        && e.getPayload().contains("\"amount\":60000")))
                .isEmpty();
    }

    @Test
    @Transactional
    void publisherMarksSuccessAndRetriesThenFails() {
        RecordingSender sender = new RecordingSender(false);
        OutboxPublisher publisher =
                new OutboxPublisher(outbox, sender, infraProperties, metrics, null);

        OutboxEvent staged = outboxService.stage(OutboxEventType.TRANSACTION_COMPLETED,
                "Transaction", "TX-1", java.util.Map.of("ref", "TX-1"), "t");
        publisher.publishOne(staged.getId());

        OutboxEvent published = outbox.findByEventId(staged.getEventId()).orElseThrow();
        assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(sender.sent).hasSize(1);

        // Failure path: stays PENDING with growing retryCount, then FAILED.
        sender.fail = true;
        OutboxEvent flaky = outboxService.stage(OutboxEventType.TRANSFER_COMPLETED,
                "Transfer", "TRF-1", java.util.Map.of("ref", "TRF-1"), "t");
        int budget = Math.max(1, infraProperties.getOutbox().getMaxRetries());
        for (int i = 0; i < budget - 1; i++) {
            publisher.publishOne(flaky.getId());
            assertThat(outbox.findByEventId(flaky.getEventId()).orElseThrow().getStatus())
                    .isEqualTo(OutboxStatus.PENDING);
        }
        publisher.publishOne(flaky.getId());
        OutboxEvent failed = outbox.findByEventId(flaky.getEventId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(budget);
        assertThat(failed.getLastError()).isNotBlank();
    }

    @Test
    void eventIdsAreUniquePerStagedEvent() {
        OutboxEvent first = outboxService.stage(OutboxEventType.TRANSACTION_COMPLETED,
                "Transaction", "A", java.util.Map.of("a", 1), "t");
        OutboxEvent second = outboxService.stage(OutboxEventType.TRANSACTION_COMPLETED,
                "Transaction", "A", java.util.Map.of("a", 1), "t");
        assertThat(first.getEventId()).isNotEqualTo(second.getEventId());
    }

    // ---------- helpers ----------

    private long freshAccount() throws Exception {
        MvcResult customer = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Out","lastName":"Box","email":"%s","phone":null}"""
                                .formatted("outbox-" + UUID.randomUUID().toString().substring(0, 8)
                                        + "@example.com")))
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

    private void deposit(long accountId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/deposits")
                        .header("Authorization", employeeAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%s,"description":"setup","idempotencyKey":"%s"}"""
                                .formatted(amount, "outbox-" + UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    /** Kafka-free sender: records sends, optionally fails. */
    static class RecordingSender extends KafkaEventPublisher {
        final List<String> sent = new ArrayList<>();
        volatile boolean fail;

        RecordingSender(boolean fail) {
            // Templates never consulted: send() is overridden below.
            super(new InfraProperties(), null);
            this.fail = fail;
        }

        @Override
        public void send(String topic, String key, String payload) {
            if (fail) {
                throw new com.nexabank.exception.InfraUnavailableException("broker down");
            }
            sent.add(topic + "|" + key);
        }
    }
}
