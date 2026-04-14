package org.example.tuumbankmock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createTransaction_in_shouldPersistTransactionAndUpdateBalance() throws Exception {
        Long accountId = createAccountWithSingleCurrency(1001L, "EUR");

        String requestJson = """
                {
                  "accountId": %d,
                  "amount": 150.00,
                  "currency": "EUR",
                  "direction": "IN",
                  "description": "Salary"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(1))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.amount").value(150.0))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.direction").value("IN"))
                .andExpect(jsonPath("$.description").value("Salary"))
                .andExpect(jsonPath("$.balanceAfterTransaction").value(150.0));

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT available_amount FROM balances WHERE account_id = ? AND currency = 'EUR'",
                BigDecimal.class,
                accountId
        );

        Integer transactionsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                Integer.class,
                accountId
        );

        assertEquals(new BigDecimal("150.00"), balance);
        assertEquals(1, transactionsCount);
    }

    @Test
    void createTransaction_out_withInsufficientFunds_shouldReturnBadRequestAndNotPersistTransaction() throws Exception {
        Long accountId = createAccountWithSingleCurrency(1002L, "EUR");

        String requestJson = """
                {
                  "accountId": %d,
                  "amount": 10.00,
                  "currency": "EUR",
                  "direction": "OUT",
                  "description": "Card payment"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Insufficient funds."))
                .andExpect(jsonPath("$.path").value("/transactions"));

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT available_amount FROM balances WHERE account_id = ? AND currency = 'EUR'",
                BigDecimal.class,
                accountId
        );

        Integer transactionsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                Integer.class,
                accountId
        );

        assertEquals(new BigDecimal("0.00"), balance);
        assertEquals(0, transactionsCount);
    }

    @Test
    void getTransactionsByAccountId_shouldReturnPersistedHistory() throws Exception {
        Long accountId = createAccountWithSingleCurrency(1003L, "EUR");

        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "accountId": %d,
                                  "amount": 100.00,
                                  "currency": "EUR",
                                  "direction": "IN",
                                  "description": "Initial deposit"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "accountId": %d,
                                  "amount": 25.00,
                                  "currency": "EUR",
                                  "direction": "OUT",
                                  "description": "Coffee"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/transactions/account/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].amount").value(100.0))
                .andExpect(jsonPath("$.transactions[0].currency").value("EUR"))
                .andExpect(jsonPath("$.transactions[0].direction").value("IN"))
                .andExpect(jsonPath("$.transactions[0].description").value("Initial deposit"))
                .andExpect(jsonPath("$.transactions[1].amount").value(25.0))
                .andExpect(jsonPath("$.transactions[1].currency").value("EUR"))
                .andExpect(jsonPath("$.transactions[1].direction").value("OUT"))
                .andExpect(jsonPath("$.transactions[1].description").value("Coffee"));
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
}