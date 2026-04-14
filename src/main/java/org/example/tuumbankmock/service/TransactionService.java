package org.example.tuumbankmock.service;

import org.example.tuumbankmock.dto.request.CreateTransactionRequest;
import org.example.tuumbankmock.dto.response.CreateTransactionResponse;
import org.example.tuumbankmock.dto.response.GetTransactionsResponse;
import org.example.tuumbankmock.dto.response.TransactionResponse;
import org.example.tuumbankmock.exception.AccountNotFoundException;
import org.example.tuumbankmock.exception.BalanceNotFoundException;
import org.example.tuumbankmock.exception.DescriptionMissingException;
import org.example.tuumbankmock.exception.IdempotencyConflictException;
import org.example.tuumbankmock.exception.InsufficientFundsException;
import org.example.tuumbankmock.exception.InvalidAmountException;
import org.example.tuumbankmock.exception.InvalidCurrencyException;
import org.example.tuumbankmock.exception.InvalidDirectionException;
import org.example.tuumbankmock.mapper.AccountMapper;
import org.example.tuumbankmock.mapper.BalanceMapper;
import org.example.tuumbankmock.mapper.TransactionMapper;
import org.example.tuumbankmock.model.Balance;
import org.example.tuumbankmock.model.Direction;
import org.example.tuumbankmock.model.Transaction;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final OutboxService outboxService;

    public TransactionService(
            AccountMapper accountMapper,
            BalanceMapper balanceMapper,
            TransactionMapper transactionMapper,
            OutboxService outboxService
    ) {
        this.accountMapper = accountMapper;
        this.balanceMapper = balanceMapper;
        this.transactionMapper = transactionMapper;
        this.outboxService = outboxService;
    }


    private void validateCreateTransactionRequest(CreateTransactionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CreateTransactionRequest cannot be null.");
        }
        if (request.getAccountId() == null) {
            throw new IllegalArgumentException("AccountId cannot be null.");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or empty.");
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

        Balance balance = balanceMapper.findByAccountIdAndCurrencyForUpdate(
                request.getAccountId(),
                request.getCurrency()
        );

        if (balance == null) {
            throw new BalanceNotFoundException("Balance not found.");
        }

        Transaction existing = transactionMapper.findByAccountIdAndIdempotencyKey(
                request.getAccountId(),
                request.getIdempotencyKey()
        );

        if (existing != null) {
            return handleExistingTransaction(existing, request);
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

        Transaction transaction = new Transaction();
        transaction.setAccountId(request.getAccountId());
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setDescription(request.getDescription());
        transaction.setDirection(request.getDirection());
        transaction.setBalanceAfterTransaction(newAmount);

        transactionMapper.insertTransaction(transaction);

        balance.setAvailableAmount(newAmount);
        balanceMapper.updateBalance(balance);

        outboxService.saveTransactionCreatedEvent(transaction);
        outboxService.saveBalanceUpdatedEvent(balance);

        return toCreateTransactionResponse(transaction);
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
            transactionResponse.setBalanceAfterTransaction(transaction.getBalanceAfterTransaction());
            transactionResponses.add(transactionResponse);
        }

        GetTransactionsResponse response = new GetTransactionsResponse();
        response.setAccountId(accountId);
        response.setTransactions(transactionResponses);
        return response;
    }

    private CreateTransactionResponse handleExistingTransaction(
            Transaction existing,
            CreateTransactionRequest request
    ) {
        if (!sameBusinessRequest(existing, request)) {
            throw new IdempotencyConflictException(
                    "Idempotency key is already used for a different transaction request."
            );
        }

        return toCreateTransactionResponse(existing);
    }

    private boolean sameBusinessRequest(Transaction existing, CreateTransactionRequest request) {
        return existing.getAccountId().equals(request.getAccountId())
                && existing.getAmount().compareTo(request.getAmount()) == 0
                && existing.getCurrency() == request.getCurrency()
                && existing.getDirection() == request.getDirection()
                && existing.getDescription().equals(request.getDescription());
    }

    private CreateTransactionResponse toCreateTransactionResponse(Transaction transaction) {
        CreateTransactionResponse response = new CreateTransactionResponse();
        response.setTransactionId(transaction.getTransactionId());
        response.setAccountId(transaction.getAccountId());
        response.setIdempotencyKey(transaction.getIdempotencyKey());
        response.setAmount(transaction.getAmount());
        response.setCurrency(transaction.getCurrency());
        response.setDirection(transaction.getDirection());
        response.setDescription(transaction.getDescription());
        response.setBalanceAfterTransaction(transaction.getBalanceAfterTransaction());
        return response;
    }
}