package org.example.tuumbankmock.service;

import org.example.tuumbankmock.mapper.OutboxEventMapper;
import org.example.tuumbankmock.model.OutboxEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxPublisher {

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.outbox.publisher.batch-size:20}")
    private int batchSize;

    public OutboxPublisher(OutboxEventMapper outboxEventMapper, RabbitTemplate rabbitTemplate) {
        this.outboxEventMapper = outboxEventMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.fixed-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventMapper.findBatchForPublish(batchSize);

        for (OutboxEvent event : batch) {
            try {
                rabbitTemplate.convertAndSend(
                        event.getExchangeName(),
                        event.getRoutingKey(),
                        event.getPayload()
                );

                outboxEventMapper.markPublished(event.getOutboxEventId());
            } catch (Exception e) {
                outboxEventMapper.markFailed(
                        event.getOutboxEventId(),
                        shortenError(e.getMessage()),
                        LocalDateTime.now().plusSeconds(backoffSeconds(event.getAttemptCount()))
                );
            }
        }
    }

    private int backoffSeconds(Integer currentAttemptCount) {
        int nextAttempt = (currentAttemptCount == null ? 1 : currentAttemptCount + 1);
        return Math.min(60, (int) Math.pow(2, Math.min(nextAttempt, 6)));
    }

    private String shortenError(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown RabbitMQ publish error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}