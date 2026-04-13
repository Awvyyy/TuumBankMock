package org.example.tuumbankmock.service;

import org.example.tuumbankmock.dto.request.CreateTransactionRequest;
import org.example.tuumbankmock.dto.response.CreateTransactionResponse;
import org.example.tuumbankmock.dto.response.GetTransactionsResponse;
import org.example.tuumbankmock.dto.response.TransactionResponse;
import org.example.tuumbankmock.exception.*;
import org.example.tuumbankmock.mapper.AccountMapper;
import org.example.tuumbankmock.mapper.BalanceMapper;
import org.example.tuumbankmock.mapper.TransactionMapper;
import org.example.tuumbankmock.model.Balance;
import org.example.tuumbankmock.model.Direction;
import org.example.tuumbankmock.model.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionService {

    private final AccountMapper accountMapper;
    private final BalanceMapper balanceMapper;
    private final TransactionMapper transactionMapper;

    public TransactionService(AccountMapper accountMapper, BalanceMapper balanceMapper, TransactionMapper transactionMapper) {
        this.accountMapper = accountMapper;
        this.balanceMapper = balanceMapper;
        this.transactionMapper = transactionMapper;
    }

    private void validateCreateTransactionRequest(CreateTransactionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CreateTransactionRequest cannot be null.");
        }
        if (request.getAccountId() == null) {
            throw new IllegalArgumentException("AccountId cannot be null.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be positive.");
        }
        if (request.getCurrency() == null) {
            throw new InvalidCurrencyException("Currency cannot be null.");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new DescriptionMissingException("Description cannot be null or empty.");
        }
        if (request.getDirection() == null) {
            throw new InvalidDirectionException("Direction cannot be null.");
        }
    }

    @Transactional
    public CreateTransactionResponse createTransaction(CreateTransactionRequest request) {
        validateCreateTransactionRequest(request);

        if (accountMapper.findById(request.getAccountId()) == null) {
            throw new AccountNotFoundException("Account not found.");
        }

        Balance balance = balanceMapper.findByAccountIdAndCurrency(request.getAccountId(), request.getCurrency());

        if (balance == null) {
            throw new BalanceNotFoundException("Balance not found.");
        }

        BigDecimal newAmount;

        if (request.getDirection() == Direction.OUT) {
            if (request.getAmount().compareTo(balance.getAvailableAmount()) > 0) {
                throw new InsufficientFundsException("Insufficient funds.");
            }
            newAmount = balance.getAvailableAmount().subtract(request.getAmount());
        } else {
            newAmount = balance.getAvailableAmount().add(request.getAmount());
        }

        balance.setAvailableAmount(newAmount);

        Transaction transaction = new Transaction();
        transaction.setAccountId(request.getAccountId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setDescription(request.getDescription());
        transaction.setDirection(request.getDirection());
        transaction.setBalanceAfterTransaction(newAmount);

        transactionMapper.insertTransaction(transaction);
        balanceMapper.updateBalance(balance);

        CreateTransactionResponse response = new CreateTransactionResponse();
        response.setTransactionId(transaction.getTransactionId());
        response.setAccountId(request.getAccountId());
        response.setAmount(request.getAmount());
        response.setCurrency(request.getCurrency());
        response.setDescription(request.getDescription());
        response.setDirection(request.getDirection());
        response.setBalanceAfterTransaction(newAmount);

        return response;
    }

    public GetTransactionsResponse getTransactionsByAccountId(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("AccountId cannot be null.");
        }

        if (accountMapper.findById(accountId) == null) {
            throw new AccountNotFoundException("Account not found.");
        }

        List<Transaction> transactions = transactionMapper.findByAccountId(accountId);
        List<TransactionResponse> transactionResponses = new ArrayList<>();

        for (Transaction transaction : transactions) {
            TransactionResponse transactionResponse = new TransactionResponse();
            transactionResponse.setTransactionId(transaction.getTransactionId());
            transactionResponse.setAmount(transaction.getAmount());
            transactionResponse.setCurrency(transaction.getCurrency());
            transactionResponse.setDirection(transaction.getDirection());
            transactionResponse.setDescription(transaction.getDescription());

            transactionResponses.add(transactionResponse);
        }

        GetTransactionsResponse response = new GetTransactionsResponse();
        response.setAccountId(accountId);
        response.setTransactions(transactionResponses);

        return response;
    }
}