package org.example.tuumbankmock.controller;

import tools.jackson.databind.ObjectMapper;
import org.example.tuumbankmock.dto.response.CreateTransactionResponse;
import org.example.tuumbankmock.dto.response.GetTransactionsResponse;
import org.example.tuumbankmock.dto.response.TransactionResponse;
import org.example.tuumbankmock.exception.AccountNotFoundException;
import org.example.tuumbankmock.exception.GlobalExceptionHandler;
import org.example.tuumbankmock.exception.InsufficientFundsException;
import org.example.tuumbankmock.model.Currency;
import org.example.tuumbankmock.model.Direction;
import org.example.tuumbankmock.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void createTransaction_shouldReturnOkAndBody() throws Exception {
        CreateTransactionResponse response = new CreateTransactionResponse();
        response.setTransactionId(101L);
        response.setAccountId(1L);
        response.setAmount(new BigDecimal("50.00"));
        response.setCurrency(Currency.EUR);
        response.setDirection(Direction.IN);
        response.setDescription("Salary");
        response.setBalanceAfterTransaction(new BigDecimal("150.00"));

        when(transactionService.createTransaction(any())).thenReturn(response);

        String requestJson = """
                {
                  "accountId": 1,
                  "amount": 50.00,
                  "currency": "EUR",
                  "direction": "IN",
                  "description": "Salary"
                }
                """;

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    void createTransaction_whenInsufficientFunds_shouldReturnBadRequest() throws Exception {
        when(transactionService.createTransaction(any()))
                .thenThrow(new InsufficientFundsException("Insufficient funds."));

        String requestJson = """
                {
                  "accountId": 1,
                  "amount": 500.00,
                  "currency": "EUR",
                  "direction": "OUT",
                  "description": "Card payment"
                }
                """;

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Insufficient funds."))
                .andExpect(jsonPath("$.path").value("/transactions"));
    }

    @Test
    void createTransaction_withInvalidEnumInBody_shouldReturnBadRequest() throws Exception {
        String requestJson = """
                {
                  "accountId": 1,
                  "amount": 50.00,
                  "currency": "INVALID",
                  "direction": "IN",
                  "description": "Salary"
                }
                """;

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid request body."))
                .andExpect(jsonPath("$.path").value("/transactions"));
    }

    @Test
    void getTransactionsByAccountId_shouldReturnOkAndBody() throws Exception {
        TransactionResponse tx1 = new TransactionResponse();
        tx1.setTransactionId(1L);
        tx1.setAmount(new BigDecimal("100.00"));
        tx1.setCurrency(Currency.EUR);
        tx1.setDirection(Direction.IN);
        tx1.setDescription("Initial deposit");

        TransactionResponse tx2 = new TransactionResponse();
        tx2.setTransactionId(2L);
        tx2.setAmount(new BigDecimal("25.00"));
        tx2.setCurrency(Currency.EUR);
        tx2.setDirection(Direction.OUT);
        tx2.setDescription("Coffee");

        GetTransactionsResponse response = new GetTransactionsResponse();
        response.setAccountId(1L);
        response.setTransactions(List.of(tx1, tx2));

        when(transactionService.getTransactionsByAccountId(1L)).thenReturn(response);

        mockMvc.perform(get("/transactions/account/1"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    void getTransactionsByAccountId_whenAccountNotFound_shouldReturnNotFound() throws Exception {
        when(transactionService.getTransactionsByAccountId(99L))
                .thenThrow(new AccountNotFoundException("Account not found."));

        mockMvc.perform(get("/transactions/account/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Account not found."))
                .andExpect(jsonPath("$.path").value("/transactions/account/99"));
    }
}