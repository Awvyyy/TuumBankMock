package org.example.tuumbankmock.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public DirectExchange accountsExchange(
            @Value("${app.rabbitmq.accounts.exchange:accounts.exchange}") String exchangeName
    ) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange balancesExchange(
            @Value("${app.rabbitmq.balances.exchange:balances.exchange}") String exchangeName
    ) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange transactionsExchange(
            @Value("${app.rabbitmq.transactions.exchange:transactions.exchange}") String exchangeName
    ) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue accountsCreatedQueue(
            @Value("${app.rabbitmq.accounts.created-queue:accounts.created.queue}") String queueName
    ) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue balancesCreatedQueue(
            @Value("${app.rabbitmq.balances.created-queue:balances.created.queue}") String queueName
    ) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue balancesUpdatedQueue(
            @Value("${app.rabbitmq.balances.updated-queue:balances.updated.queue}") String queueName
    ) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue transactionsCreatedQueue(
            @Value("${app.rabbitmq.transactions.created-queue:transactions.created.queue}") String queueName
    ) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding accountsCreatedBinding(
            Queue accountsCreatedQueue,
            DirectExchange accountsExchange
    ) {
        return BindingBuilder.bind(accountsCreatedQueue)
                .to(accountsExchange)
                .with("accounts.created");
    }

    @Bean
    public Binding balancesCreatedBinding(
            Queue balancesCreatedQueue,
            DirectExchange balancesExchange
    ) {
        return BindingBuilder.bind(balancesCreatedQueue)
                .to(balancesExchange)
                .with("balances.created");
    }

    @Bean
    public Binding balancesUpdatedBinding(
            Queue balancesUpdatedQueue,
            DirectExchange balancesExchange
    ) {
        return BindingBuilder.bind(balancesUpdatedQueue)
                .to(balancesExchange)
                .with("balances.updated");
    }

    @Bean
    public Binding transactionsCreatedBinding(
            Queue transactionsCreatedQueue,
            DirectExchange transactionsExchange
    ) {
        return BindingBuilder.bind(transactionsCreatedQueue)
                .to(transactionsExchange)
                .with("transactions.created");
    }
}