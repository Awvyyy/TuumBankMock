package org.example.tuumbankmock.service;

import org.example.tuumbankmock.dto.request.CreateTransactionRequest;
import org.example.tuumbankmock.dto.response.CreateTransactionResponse;
import org.example.tuumbankmock.exception.AccountNotFoundException;
import org.example.tuumbankmock.exception.BalanceNotFoundException;
import org.example.tuumbankmock.exception.InsufficientFundsException;
import org.example.tuumbankmock.mapper.AccountMapper;
import org.example.tuumbankmock.mapper.BalanceMapper;
import org.example.tuumbankmock.mapper.TransactionMapper;
import org.example.tuumbankmock.model.Account;
import org.example.tuumbankmock.model.Balance;
import org.example.tuumbankmock.model.Currency;
import org.example.tuumbankmock.model.Direction;
import org.example.tuumbankmock.model.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private BalanceMapper balanceMapper;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void createTransaction_inDirection_shouldIncreaseBalanceAndReturnResponse() {
        Long accountId = 1L;

        when(accountMapper.findById(accountId)).thenReturn(account(accountId));

        Balance balance = balance(accountId, Currency.EUR, "100.00");
        when(balanceMapper.findByAccountIdAndCurrencyForUpdate(accountId, Currency.EUR))
                .thenReturn(balance);

        doAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setTransactionId(101L);
            return null;
        }).when(transactionMapper).insertTransaction(any(Transaction.class));

        CreateTransactionRequest request = request(
                accountId,
                "idem-001",
                "50.00",
                Currency.EUR,
                Direction.IN,
                "Salary"
        );

        CreateTransactionResponse response = transactionService.createTransaction(request);

        assertEquals(101L, response.getTransactionId());
        assertEquals(accountId, response.getAccountId());
        assertEquals(new BigDecimal("50.00"), response.getAmount());
        assertEquals(Currency.EUR, response.getCurrency());
        assertEquals(Direction.IN, response.getDirection());
        assertEquals("Salary", response.getDescription());
        assertEquals(new BigDecimal("150.00"), response.getBalanceAfterTransaction());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insertTransaction(transactionCaptor.capture());

        Transaction savedTransaction = transactionCaptor.getValue();
        assertEquals(accountId, savedTransaction.getAccountId());
        assertEquals(new BigDecimal("50.00"), savedTransaction.getAmount());
        assertEquals(Currency.EUR, savedTransaction.getCurrency());
        assertEquals(Direction.IN, savedTransaction.getDirection());
        assertEquals("Salary", savedTransaction.getDescription());
        assertEquals(new BigDecimal("150.00"), savedTransaction.getBalanceAfterTransaction());

        ArgumentCaptor<Balance> balanceCaptor = ArgumentCaptor.forClass(Balance.class);
        verify(balanceMapper).updateBalance(balanceCaptor.capture());

        Balance updatedBalance = balanceCaptor.getValue();
        assertEquals(new BigDecimal("150.00"), updatedBalance.getAvailableAmount());
    }

    @Test
    void createTransaction_outDirection_shouldDecreaseBalanceAndReturnResponse() {
        Long accountId = 1L;

        when(accountMapper.findById(accountId)).thenReturn(account(accountId));

        Balance balance = balance(accountId, Currency.EUR, "100.00");
        when(balanceMapper.findByAccountIdAndCurrencyForUpdate(accountId, Currency.EUR))
                .thenReturn(balance);

        doAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setTransactionId(202L);
            return null;
        }).when(transactionMapper).insertTransaction(any(Transaction.class));

        CreateTransactionRequest request = request(
                accountId,
                "idem-002",
                "40.00",
                Currency.EUR,
                Direction.OUT,
                "ATM withdrawal"
        );

        CreateTransactionResponse response = transactionService.createTransaction(request);

        assertEquals(202L, response.getTransactionId());
        assertEquals(accountId, response.getAccountId());
        assertEquals(new BigDecimal("40.00"), response.getAmount());
        assertEquals(Currency.EUR, response.getCurrency());
        assertEquals(Direction.OUT, response.getDirection());
        assertEquals("ATM withdrawal", response.getDescription());
        assertEquals(new BigDecimal("60.00"), response.getBalanceAfterTransaction());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insertTransaction(transactionCaptor.capture());
        assertEquals(new BigDecimal("60.00"), transactionCaptor.getValue().getBalanceAfterTransaction());

        ArgumentCaptor<Balance> balanceCaptor = ArgumentCaptor.forClass(Balance.class);
        verify(balanceMapper).updateBalance(balanceCaptor.capture());
        assertEquals(new BigDecimal("60.00"), balanceCaptor.getValue().getAvailableAmount());
    }

    @Test
    void createTransaction_outDirection_withInsufficientFunds_shouldThrowAndNotPersistAnything() {
        Long accountId = 1L;

        when(accountMapper.findById(accountId)).thenReturn(account(accountId));

        Balance balance = balance(accountId, Currency.EUR, "30.00");
        when(balanceMapper.findByAccountIdAndCurrencyForUpdate(accountId, Currency.EUR))
                .thenReturn(balance);

        CreateTransactionRequest request = request(
                accountId,
                "idem-003",
                "40.00",
                Currency.EUR,
                Direction.OUT,
                "Card payment"
        );

        assertThrows(
                InsufficientFundsException.class,
                () -> transactionService.createTransaction(request)
        );

        verify(transactionMapper, never()).insertTransaction(any(Transaction.class));
        verify(balanceMapper, never()).updateBalance(any(Balance.class));
    }

    @Test
    void createTransaction_whenAccountDoesNotExist_shouldThrowAccountNotFoundException() {
        Long accountId = 999L;

        when(accountMapper.findById(accountId)).thenReturn(null);

        CreateTransactionRequest request = request(
                accountId,
                "idem-004",
                "10.00",
                Currency.EUR,
                Direction.IN,
                "Top up"
        );

        assertThrows(
                AccountNotFoundException.class,
                () -> transactionService.createTransaction(request)
        );

        verify(balanceMapper, never()).findByAccountIdAndCurrencyForUpdate(any(Long.class), any(Currency.class));
        verify(transactionMapper, never()).insertTransaction(any(Transaction.class));
        verify(balanceMapper, never()).updateBalance(any(Balance.class));
    }

    @Test
    void createTransaction_whenBalanceDoesNotExist_shouldThrowBalanceNotFoundException() {
        Long accountId = 1L;

        when(accountMapper.findById(accountId)).thenReturn(account(accountId));
        when(balanceMapper.findByAccountIdAndCurrencyForUpdate(accountId, Currency.EUR))
                .thenReturn(null);

        CreateTransactionRequest request = request(
                accountId,
                "idem-005",
                "10.00",
                Currency.EUR,
                Direction.IN,
                "Top up"
        );

        assertThrows(
                BalanceNotFoundException.class,
                () -> transactionService.createTransaction(request)
        );

        verify(transactionMapper, never()).insertTransaction(any(Transaction.class));
        verify(balanceMapper, never()).updateBalance(any(Balance.class));
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

    private Account account(Long accountId) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setCustomerId(123L);
        account.setCountry("EE");
        return account;
    }

    private Balance balance(Long accountId, Currency currency, String amount) {
        Balance balance = new Balance();
        balance.setBalanceId(10L);
        balance.setAccountId(accountId);
        balance.setCurrency(currency);
        balance.setAvailableAmount(new BigDecimal(amount));
        return balance;
    }
}