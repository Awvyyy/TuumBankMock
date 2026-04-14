package org.example.tuumbankmock.service;

import org.example.tuumbankmock.dto.request.CreateAccountRequest;
import org.example.tuumbankmock.dto.response.BalanceResponse;
import org.example.tuumbankmock.dto.response.CreateAccountResponse;
import org.example.tuumbankmock.dto.response.GetAccountResponse;
import org.example.tuumbankmock.model.Account;
import org.example.tuumbankmock.model.Balance;
import org.example.tuumbankmock.model.Currency;
import org.example.tuumbankmock.mapper.AccountMapper;
import org.example.tuumbankmock.mapper.BalanceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AccountService {

    private final AccountMapper accountMapper;
    private final BalanceMapper balanceMapper;
    private final OutboxService outboxService;

    public AccountService(
            AccountMapper accountMapper,
            BalanceMapper balanceMapper,
            OutboxService outboxService
    ) {
        this.accountMapper = accountMapper;
        this.balanceMapper = balanceMapper;
        this.outboxService = outboxService;
    }


    private void validateCreateAccountRequest(CreateAccountRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }

        if (request.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer ID must not be null");
        }

        if (request.getCountry() == null || request.getCountry().isBlank()) {
            throw new IllegalArgumentException("Country must not be blank");
        }

        if (request.getCurrencies() == null || request.getCurrencies().isEmpty()) {
            throw new IllegalArgumentException("Currencies must not be empty");
        }

        Set<Currency> uniqueCurrencies = new HashSet<>(request.getCurrencies());
        if (uniqueCurrencies.size() != request.getCurrencies().size()) {
            throw new IllegalArgumentException("Duplicate currencies are not allowed");
        }
    }

    @Transactional
    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        validateCreateAccountRequest(request);

        Account account = new Account();
        account.setCustomerId(request.getCustomerId());
        account.setCountry(request.getCountry());

        accountMapper.insertAccount(account);
        outboxService.saveAccountCreatedEvent(account);

        List<BalanceResponse> balanceResponses = new ArrayList<>();

        for (Currency currency : request.getCurrencies()) {
            Balance balance = new Balance();
            balance.setAccountId(account.getAccountId());
            balance.setCurrency(currency);
            balance.setAvailableAmount(BigDecimal.ZERO);

            balanceMapper.insertBalance(balance);
            outboxService.saveBalanceCreatedEvent(balance);

            BalanceResponse balanceResponse = new BalanceResponse();
            balanceResponse.setCurrency(balance.getCurrency());
            balanceResponse.setAvailableAmount(balance.getAvailableAmount());
            balanceResponses.add(balanceResponse);
        }

        CreateAccountResponse response = new CreateAccountResponse();
        response.setAccountId(account.getAccountId());
        response.setCustomerId(account.getCustomerId());
        response.setBalances(balanceResponses);

        return response;
    }

    public GetAccountResponse getAccountById(Long accountId) {
        Account account = accountMapper.findById(accountId);

        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }

        List<Balance> balances = balanceMapper.findByAccountId(accountId);
        List<BalanceResponse> balanceResponses = new ArrayList<>();

        for (Balance balance : balances) {
            BalanceResponse balanceResponse = new BalanceResponse();
            balanceResponse.setCurrency(balance.getCurrency());
            balanceResponse.setAvailableAmount(balance.getAvailableAmount());
            balanceResponses.add(balanceResponse);
        }
        GetAccountResponse response = new GetAccountResponse();
        response.setAccountId(account.getAccountId());
        response.setCustomerId(account.getCustomerId());
        response.setBalances(balanceResponses);

        return response;
    }
}