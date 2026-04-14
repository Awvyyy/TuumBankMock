package org.example.tuumbankmock;

import org.example.tuumbankmock.dto.request.CreateTransactionRequest;
import org.example.tuumbankmock.model.Currency;
import org.example.tuumbankmock.model.Direction;
import org.example.tuumbankmock.service.OutboxPublisher;
import org.example.tuumbankmock.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.eq;

class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void createTransaction_shouldWritePendingOutboxEvent() throws Exception {
        Long accountId = createAccountWithSingleCurrency(4001L, "EUR");

        transactionService.createTransaction(request(
                accountId,
                "outbox-deposit-001",
                "100.00",
                Currency.EUR,
                Direction.IN,
                "Initial deposit"
        ));

        Long transactionId = jdbcTemplate.queryForObject(
                "SELECT transaction_id FROM transactions WHERE account_id = ? AND idempotency_key = ?",
                Long.class,
                accountId,
                "outbox-deposit-001"
        );

        Long outboxEventId = jdbcTemplate.queryForObject(
                "SELECT outbox_event_id FROM outbox_events WHERE aggregate_id = ? AND aggregate_type = 'TRANSACTION'",
                Long.class,
                transactionId
        );

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE outbox_event_id = ?",
                String.class,
                outboxEventId
        );

        String eventType = jdbcTemplate.queryForObject(
                "SELECT event_type FROM outbox_events WHERE outbox_event_id = ?",
                String.class,
                outboxEventId
        );

        String routingKey = jdbcTemplate.queryForObject(
                "SELECT routing_key FROM outbox_events WHERE outbox_event_id = ?",
                String.class,
                outboxEventId
        );

        String payload = jdbcTemplate.queryForObject(
                "SELECT payload FROM outbox_events WHERE outbox_event_id = ?",
                String.class,
                outboxEventId
        );

        assertNotNull(outboxEventId);
        assertEquals("PENDING", status);
        assertEquals("TRANSACTION_CREATED", eventType);
        assertEquals("transactions.created", routingKey);
        assertNotNull(payload);
    }

    @Test
    void publishPendingEvents_shouldSendToRabbitAndMarkOutboxEventAsPublished() throws Exception {
        Long accountId = createAccountWithSingleCurrency(4002L, "EUR");

        transactionService.createTransaction(request(
                accountId,
                "outbox-withdraw-001",
                "50.00",
                Currency.EUR,
                Direction.IN,
                "Salary"
        ));

        Long transactionId = jdbcTemplate.queryForObject(
                "SELECT transaction_id FROM transactions WHERE account_id = ? AND idempotency_key = ?",
                Long.class,
                accountId,
                "outbox-withdraw-001"
        );

        Long outboxEventId = jdbcTemplate.queryForObject(
                "SELECT outbox_event_id FROM outbox_events WHERE aggregate_id = ? AND aggregate_type = 'TRANSACTION'",
                Long.class,
                transactionId
        );

        outboxPublisher.publishPendingEvents();

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq("transactions.exchange"),
                eq("transactions.created"),
                anyString()
        );

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE outbox_event_id = ?",
                String.class,
                outboxEventId
        );

        Integer attemptCount = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM outbox_events WHERE outbox_event_id = ?",
                Integer.class,
                outboxEventId
        );

        assertEquals("PUBLISHED", status);
        assertEquals(0, attemptCount);
    }

    @Test
    void createAccount_shouldWriteAccountAndBalanceOutboxEvents() throws Exception {
        String requestJson = """
            {
              "customerId": 5001,
              "country": "EE",
              "currencies": ["EUR", "USD"]
            }
            """;

        mockMvc.perform(post("/accounts")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        Long accountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM accounts WHERE customer_id = ?",
                Long.class,
                5001L
        );

        Integer accountCreatedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'ACCOUNT_CREATED' AND aggregate_id = ?",
                Integer.class,
                accountId
        );

        Integer balanceCreatedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'BALANCE_CREATED'",
                Integer.class
        );

        assertEquals(1, accountCreatedEvents);
        assertEquals(2, balanceCreatedEvents);
    }

    @Test
    void createTransaction_shouldWriteTransactionAndBalanceUpdatedOutboxEvents() throws Exception {
        Long accountId = createAccountWithSingleCurrency(5002L, "EUR");

        transactionService.createTransaction(request(
                accountId,
                "rabbit-balance-update-001",
                "100.00",
                Currency.EUR,
                Direction.IN,
                "Initial deposit"
        ));

        Integer transactionCreatedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'TRANSACTION_CREATED'",
                Integer.class
        );

        Integer balanceUpdatedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'BALANCE_UPDATED'",
                Integer.class
        );

        assertEquals(1, transactionCreatedEvents);
        assertEquals(1, balanceUpdatedEvents);
    }

    @Test
    void publishPendingEvents_whenRabbitFails_shouldMarkOutboxEventAsFailedAndIncreaseAttemptCount() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("Rabbit unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(
                        anyString(),
                        anyString(),
                        anyString()
                );

        Long accountId = createAccountWithSingleCurrency(4003L, "EUR");

        transactionService.createTransaction(request(
                accountId,
                "outbox-fail-001",
                "75.00",
                Currency.EUR,
                Direction.IN,
                "Bonus"
        ));

        Long transactionId = jdbcTemplate.queryForObject(
                "SELECT transaction_id FROM transactions WHERE account_id = ? AND idempotency_key = ?",
                Long.class,
                accountId,
                "outbox-fail-001"
        );

        Long outboxEventId = jdbcTemplate.queryForObject(
                "SELECT outbox_event_id FROM outbox_events WHERE aggregate_id = ? AND aggregate_type = 'TRANSACTION'",
                Long.class,
                transactionId
        );

        outboxPublisher.publishPendingEvents();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE outbox_event_id = ?",
                String.class,
                outboxEventId
        );

        Integer attemptCount = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM outbox_events WHERE outbox_event_id = ?",
                Integer.class,
                outboxEventId
        );

        String lastError = jdbcTemplate.queryForObject(
                "SELECT last_error FROM outbox_events WHERE outbox_event_id = ?",
                String.class,
                outboxEventId
        );

        assertEquals("FAILED", status);
        assertEquals(1, attemptCount);
        assertNotNull(lastError);
    }

    private Long createAccountWithSingleCurrency(Long customerId, String currency) throws Exception {
        String requestJson = """
                {
                  "customerId": %d,
                  "country": "EE",
                  "currencies": ["%s"]
                }
                """.formatted(customerId, currency);

        mockMvc.perform(post("/accounts")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        return jdbcTemplate.queryForObject(
                "SELECT account_id FROM accounts WHERE customer_id = ?",
                Long.class,
                customerId
        );
    }

    private CreateTransactionRequest request(
            Long accountId,
            String idempotencyKey,
            String amount,
            Currency currency,
            Direction direction,
            String description
    ) {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAccountId(accountId);
        request.setIdempotencyKey(idempotencyKey);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency(currency);
        request.setDirection(direction);
        request.setDescription(description);
        return request;
    }
}