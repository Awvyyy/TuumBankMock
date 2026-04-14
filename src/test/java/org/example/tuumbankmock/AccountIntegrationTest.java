package org.example.tuumbankmock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createAccount_shouldPersistAccountAndBalances() throws Exception {
        String requestJson = """
                {
                  "customerId": 123,
                  "country": "EE",
                  "currencies": ["EUR", "USD"]
                }
                """;

        mockMvc.perform(post("/accounts")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.customerId").value(123))
                .andExpect(jsonPath("$.balances", hasSize(2)))
                .andExpect(jsonPath("$.balances[0].currency").value("EUR"))
                .andExpect(jsonPath("$.balances[0].availableAmount").value(0))
                .andExpect(jsonPath("$.balances[1].currency").value("USD"))
                .andExpect(jsonPath("$.balances[1].availableAmount").value(0));

        Integer accountsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts",
                Integer.class
        );
        Integer balancesCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM balances",
                Integer.class
        );

        assertEquals(1, accountsCount);
        assertEquals(2, balancesCount);
    }

    @Test
    void getAccountById_shouldReturnPersistedBalances() throws Exception {
        String createRequest = """
                {
                  "customerId": 777,
                  "country": "EE",
                  "currencies": ["EUR", "GBP"]
                }
                """;

        mockMvc.perform(post("/accounts")
                        .contentType("application/json")
                        .content(createRequest))
                .andExpect(status().isOk());

        Long accountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM accounts WHERE customer_id = ?",
                Long.class,
                777L
        );

        mockMvc.perform(get("/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.customerId").value(777))
                .andExpect(jsonPath("$.balances", hasSize(2)))
                .andExpect(jsonPath("$.balances[0].currency").value("EUR"))
                .andExpect(jsonPath("$.balances[0].availableAmount").value(0))
                .andExpect(jsonPath("$.balances[1].currency").value("GBP"))
                .andExpect(jsonPath("$.balances[1].availableAmount").value(0));
    }

    @Test
    void createAccount_withDuplicateCurrencies_shouldReturnBadRequest() throws Exception {
        String requestJson = """
                {
                  "customerId": 123,
                  "country": "EE",
                  "currencies": ["EUR", "EUR"]
                }
                """;

        mockMvc.perform(post("/accounts")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Duplicate currencies are not allowed"))
                .andExpect(jsonPath("$.path").value("/accounts"));
    }
}