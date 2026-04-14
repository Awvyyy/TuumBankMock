package org.example.tuumbankmock;

import org.example.tuumbankmock.dto.request.CreateTransactionRequest;
import org.example.tuumbankmock.exception.InsufficientFundsException;
import org.example.tuumbankmock.model.Currency;
import org.example.tuumbankmock.model.Direction;
import org.example.tuumbankmock.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionService transactionService;

    @Test
    void concurrentWithdrawals_onlyOneShouldSucceed_andBalanceMustNotGoNegative() throws Exception {
        Long accountId = createAccountWithSingleCurrency(2001L, "EUR");

        transactionService.createTransaction(request(
                accountId,
                "deposit-002",
                "100.00",
                Currency.EUR,
                Direction.IN,
                "Initial deposit"
        ));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<String>> tasks = List.of(
                withdrawalTask(accountId, "withdraw-001", ready, start),
                withdrawalTask(accountId, "withdraw-002", ready, start)
        );

        List<Future<String>> futures = new ArrayList<>();
        for (Callable<String> task : tasks) {
            futures.add(executor.submit(task));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "Threads did not get ready in time");
        start.countDown();

        int successCount = 0;
        int insufficientFundsCount = 0;
        List<String> results = new ArrayList<>();

        for (Future<String> future : futures) {
            String result = future.get(10, TimeUnit.SECONDS);
            results.add(result);

            if ("SUCCESS".equals(result)) {
                successCount++;
            } else if ("INSUFFICIENT_FUNDS".equals(result)) {
                insufficientFundsCount++;
            }
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor did not terminate in time");

        BigDecimal finalBalance = jdbcTemplate.queryForObject(
                "SELECT available_amount FROM balances WHERE account_id = ? AND currency = 'EUR'",
                BigDecimal.class,
                accountId
        );

        Integer transactionsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                Integer.class,
                accountId
        );

        assertEquals(1, successCount, "Exactly one withdrawal must succeed. Results: " + results);
        assertEquals(1, insufficientFundsCount, "Exactly one withdrawal must fail with insufficient funds. Results: " + results);
        assertEquals(new BigDecimal("20.00"), finalBalance, "Final balance must be 20.00");
        assertEquals(2, transactionsCount, "There should be 2 transactions total: 1 deposit + 1 successful withdrawal");
    }

    private Callable<String> withdrawalTask(
            Long accountId,
            String idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await();

            try {
                transactionService.createTransaction(request(
                        accountId,
                        idempotencyKey,
                        "80.00",
                        Currency.EUR,
                        Direction.OUT,
                        "Concurrent withdrawal"
                ));
                return "SUCCESS";
            } catch (InsufficientFundsException e) {
                return "INSUFFICIENT_FUNDS";
            } catch (Exception e) {
                return e.getClass().getSimpleName();
            }
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