package org.example.tuumbankmock;

import org.example.tuumbankmock.dto.request.CreateTransactionRequest;
import org.example.tuumbankmock.dto.response.CreateTransactionResponse;
import org.example.tuumbankmock.model.Currency;
import org.example.tuumbankmock.model.Direction;
import org.example.tuumbankmock.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionService transactionService;

    @Test
    void sameIdempotencyKey_andSamePayload_shouldReturnSameTransaction_andApplyMoneyOnlyOnce() throws Exception {
        Long accountId = createAccountWithSingleCurrency(3001L, "EUR");

        String requestJson = """
                {
                  "accountId": %d,
                  "idempotencyKey": "idem-001",
                  "amount": 50.00,
                  "currency": "EUR",
                  "direction": "IN",
                  "description": "Salary"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.idempotencyKey").value("idem-001"))
                .andExpect(jsonPath("$.amount").value(50.0))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.direction").value("IN"))
                .andExpect(jsonPath("$.description").value("Salary"))
                .andExpect(jsonPath("$.balanceAfterTransaction").value(50.0));

        Long transactionId = jdbcTemplate.queryForObject(
                "SELECT transaction_id FROM transactions WHERE account_id = ? AND idempotency_key = ?",
                Long.class,
                accountId,
                "idem-001"
        );

        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.intValue()))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.idempotencyKey").value("idem-001"))
                .andExpect(jsonPath("$.balanceAfterTransaction").value(50.0));

        Integer transactionsWithSameKey = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ? AND idempotency_key = ?",
                Integer.class,
                accountId,
                "idem-001"
        );

        BigDecimal finalBalance = jdbcTemplate.queryForObject(
                "SELECT available_amount FROM balances WHERE account_id = ? AND currency = 'EUR'",
                BigDecimal.class,
                accountId
        );

        assertEquals(1, transactionsWithSameKey);
        assertEquals(new BigDecimal("50.00"), finalBalance);
    }

    @Test
    void sameIdempotencyKey_andDifferentPayload_shouldReturnConflict_andNotChangeState() throws Exception {
        Long accountId = createAccountWithSingleCurrency(3002L, "EUR");

        String firstRequest = """
                {
                  "accountId": %d,
                  "idempotencyKey": "idem-002",
                  "amount": 50.00,
                  "currency": "EUR",
                  "direction": "IN",
                  "description": "Salary"
                }
                """.formatted(accountId);

        String secondRequestWithDifferentAmount = """
                {
                  "accountId": %d,
                  "idempotencyKey": "idem-002",
                  "amount": 70.00,
                  "currency": "EUR",
                  "direction": "IN",
                  "description": "Salary"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content(firstRequest))
                .andExpect(status().isOk());

        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content(secondRequestWithDifferentAmount))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Idempotency key is already used for a different transaction request."))
                .andExpect(jsonPath("$.path").value("/transactions"));

        Integer transactionsWithSameKey = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ? AND idempotency_key = ?",
                Integer.class,
                accountId,
                "idem-002"
        );

        BigDecimal finalBalance = jdbcTemplate.queryForObject(
                "SELECT available_amount FROM balances WHERE account_id = ? AND currency = 'EUR'",
                BigDecimal.class,
                accountId
        );

        BigDecimal storedAmount = jdbcTemplate.queryForObject(
                "SELECT amount FROM transactions WHERE account_id = ? AND idempotency_key = ?",
                BigDecimal.class,
                accountId,
                "idem-002"
        );

        assertEquals(1, transactionsWithSameKey);
        assertEquals(new BigDecimal("50.00"), finalBalance);
        assertEquals(new BigDecimal("50.00"), storedAmount);
    }

    @Test
    void concurrentSameIdempotencyKey_andSamePayload_shouldCreateOnlyOneWithdrawal() throws Exception {
        Long accountId = createAccountWithSingleCurrency(3003L, "EUR");

        transactionService.createTransaction(request(
                accountId,
                "deposit-001",
                "100.00",
                Currency.EUR,
                Direction.IN,
                "Initial deposit"
        ));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Long>> tasks = List.of(
                withdrawalTask(accountId, ready, start),
                withdrawalTask(accountId, ready, start)
        );

        List<Future<Long>> futures = new ArrayList<>();
        for (Callable<Long> task : tasks) {
            futures.add(executor.submit(task));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "Threads did not get ready in time");
        start.countDown();

        Long txId1 = futures.get(0).get(10, TimeUnit.SECONDS);
        Long txId2 = futures.get(1).get(10, TimeUnit.SECONDS);

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor did not terminate in time");

        Integer duplicatedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ? AND idempotency_key = ?",
                Integer.class,
                accountId,
                "idem-withdraw-001"
        );

        Integer totalTransactions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                Integer.class,
                accountId
        );

        BigDecimal finalBalance = jdbcTemplate.queryForObject(
                "SELECT available_amount FROM balances WHERE account_id = ? AND currency = 'EUR'",
                BigDecimal.class,
                accountId
        );

        assertEquals(txId1, txId2, "Both concurrent calls must return the same transaction id");
        assertEquals(1, duplicatedRows, "Only one row with the same idempotency key must exist");
        assertEquals(2, totalTransactions, "Only deposit + one withdrawal should exist");
        assertEquals(new BigDecimal("20.00"), finalBalance);
    }

    private Callable<Long> withdrawalTask(Long accountId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();

            CreateTransactionResponse response = transactionService.createTransaction(request(
                    accountId,
                    "idem-withdraw-001",
                    "80.00",
                    Currency.EUR,
                    Direction.OUT,
                    "Concurrent withdrawal"
            ));

            return response.getTransactionId();
        };
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