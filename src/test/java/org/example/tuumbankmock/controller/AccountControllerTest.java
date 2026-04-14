package org.example.tuumbankmock.controller;

import tools.jackson.databind.ObjectMapper;
import org.example.tuumbankmock.dto.response.BalanceResponse;
import org.example.tuumbankmock.dto.response.CreateAccountResponse;
import org.example.tuumbankmock.dto.response.GetAccountResponse;
import org.example.tuumbankmock.exception.GlobalExceptionHandler;
import org.example.tuumbankmock.model.Currency;
import org.example.tuumbankmock.service.AccountService;
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

@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @Test
    void createAccount_shouldReturnOkAndBody() throws Exception {
        BalanceResponse eurBalance = new BalanceResponse();
        eurBalance.setCurrency(Currency.EUR);
        eurBalance.setAvailableAmount(BigDecimal.ZERO);

        BalanceResponse usdBalance = new BalanceResponse();
        usdBalance.setCurrency(Currency.USD);
        usdBalance.setAvailableAmount(BigDecimal.ZERO);

        CreateAccountResponse response = new CreateAccountResponse();
        response.setAccountId(1L);
        response.setCustomerId(123L);
        response.setBalances(List.of(eurBalance, usdBalance));

        when(accountService.createAccount(any())).thenReturn(response);

        String requestJson = """
                {
                  "customerId": 123,
                  "country": "EE",
                  "currencies": ["EUR", "USD"]
                }
                """;

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    void createAccount_whenValidationFails_shouldReturnBadRequest() throws Exception {
        when(accountService.createAccount(any()))
                .thenThrow(new IllegalArgumentException("Duplicate currencies are not allowed"));

        String requestJson = """
                {
                  "customerId": 123,
                  "country": "EE",
                  "currencies": ["EUR", "EUR"]
                }
                """;

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Duplicate currencies are not allowed"))
                .andExpect(jsonPath("$.path").value("/accounts"));
    }

    @Test
    void getAccountById_shouldReturnOkAndBody() throws Exception {
        BalanceResponse eurBalance = new BalanceResponse();
        eurBalance.setCurrency(Currency.EUR);
        eurBalance.setAvailableAmount(new BigDecimal("125.00"));

        BalanceResponse usdBalance = new BalanceResponse();
        usdBalance.setCurrency(Currency.USD);
        usdBalance.setAvailableAmount(new BigDecimal("50.00"));

        GetAccountResponse response = new GetAccountResponse();
        response.setAccountId(1L);
        response.setCustomerId(123L);
        response.setBalances(List.of(eurBalance, usdBalance));

        when(accountService.getAccountById(1L)).thenReturn(response);

        mockMvc.perform(get("/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    void getAccountById_whenNotFound_shouldReturnBadRequest() throws Exception {
        when(accountService.getAccountById(99L))
                .thenThrow(new IllegalArgumentException("Account not found"));

        mockMvc.perform(get("/accounts/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Account not found"))
                .andExpect(jsonPath("$.path").value("/accounts/99"));
    }
}