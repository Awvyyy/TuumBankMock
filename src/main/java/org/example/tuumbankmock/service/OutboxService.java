package org.example.tuumbankmock.service;

import org.example.tuumbankmock.dto.event.AccountCreatedEvent;
import org.example.tuumbankmock.dto.event.BalanceCreatedEvent;
import org.example.tuumbankmock.dto.event.BalanceUpdatedEvent;
import org.example.tuumbankmock.dto.event.TransactionCreatedEvent;
import org.example.tuumbankmock.mapper.OutboxEventMapper;
import org.example.tuumbankmock.model.Account;
import org.example.tuumbankmock.model.Balance;
import org.example.tuumbankmock.model.OutboxEvent;
import org.example.tuumbankmock.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxService {

    private final OutboxEventMapper outboxEventMapper;
    private final JsonMapper jsonMapper;

    @Value("${app.rabbitmq.accounts.exchange:accounts.exchange}")
    private String accountsExchange;

    @Value("${app.rabbitmq.balances.exchange:balances.exchange}")
    private String balancesExchange;

    @Value("${app.rabbitmq.transactions.exchange:transactions.exchange}")
    private String transactionsExchange;

    public OutboxService(OutboxEventMapper outboxEventMapper, JsonMapper jsonMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.jsonMapper = jsonMapper;
    }

    public void saveAccountCreatedEvent(Account account) {
        try {
            AccountCreatedEvent payload = new AccountCreatedEvent();
            payload.setAccountId(account.getAccountId());
            payload.setCustomerId(account.getCustomerId());
            payload.setCountry(account.getCountry());

            insertOutboxEvent(
                    "ACCOUNT_CREATED",
                    "ACCOUNT",
                    account.getAccountId(),
                    accountsExchange,
                    "accounts.created",
                    jsonMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write ACCOUNT_CREATED outbox event.", e);
        }
    }

    public void saveBalanceCreatedEvent(Balance balance) {
        try {
            BalanceCreatedEvent payload = new BalanceCreatedEvent();
            payload.setBalanceId(balance.getBalanceId());
            payload.setAccountId(balance.getAccountId());
            payload.setCurrency(balance.getCurrency());
            payload.setAvailableAmount(balance.getAvailableAmount());

            insertOutboxEvent(
                    "BALANCE_CREATED",
                    "BALANCE",
                    balance.getBalanceId(),
                    balancesExchange,
                    "balances.created",
                    jsonMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write BALANCE_CREATED outbox event.", e);
        }
    }

    public void saveBalanceUpdatedEvent(Balance balance) {
        try {
            BalanceUpdatedEvent payload = new BalanceUpdatedEvent();
            payload.setBalanceId(balance.getBalanceId());
            payload.setAccountId(balance.getAccountId());
            payload.setCurrency(balance.getCurrency());
            payload.setAvailableAmount(balance.getAvailableAmount());

            insertOutboxEvent(
                    "BALANCE_UPDATED",
                    "BALANCE",
                    balance.getBalanceId(),
                    balancesExchange,
                    "balances.updated",
                    jsonMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write BALANCE_UPDATED outbox event.", e);
        }
    }

    public void saveTransactionCreatedEvent(Transaction transaction) {
        try {
            TransactionCreatedEvent payload = new TransactionCreatedEvent();
            payload.setTransactionId(transaction.getTransactionId());
            payload.setAccountId(transaction.getAccountId());
            payload.setAmount(transaction.getAmount());
            payload.setCurrency(transaction.getCurrency());
            payload.setDirection(transaction.getDirection());
            payload.setDescription(transaction.getDescription());
            payload.setBalanceAfterTransaction(transaction.getBalanceAfterTransaction());

            insertOutboxEvent(
                    "TRANSACTION_CREATED",
                    "TRANSACTION",
                    transaction.getTransactionId(),
                    transactionsExchange,
                    "transactions.created",
                    jsonMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write TRANSACTION_CREATED outbox event.", e);
        }
    }

    private void insertOutboxEvent(
            String eventType,
            String aggregateType,
            Long aggregateId,
            String exchangeName,
            String routingKey,
            String payload
    ) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventType(eventType);
        outboxEvent.setAggregateType(aggregateType);
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setExchangeName(exchangeName);
        outboxEvent.setRoutingKey(routingKey);
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus("PENDING");
        outboxEventMapper.insert(outboxEvent);
    }
}