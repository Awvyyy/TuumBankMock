package org.example.tuumbankmock.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.tuumbankmock.model.OutboxEvent;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper {

    @Insert("""
        INSERT INTO outbox_events (
            event_type,
            aggregate_type,
            aggregate_id,
            exchange_name,
            routing_key,
            payload,
            status
        )
        VALUES (
            #{eventType},
            #{aggregateType},
            #{aggregateId},
            #{exchangeName},
            #{routingKey},
            #{payload},
            #{status}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "outboxEventId")
    void insert(OutboxEvent outboxEvent);

    @Select("""
        SELECT
            outbox_event_id AS outboxEventId,
            event_type AS eventType,
            aggregate_type AS aggregateType,
            aggregate_id AS aggregateId,
            exchange_name AS exchangeName,
            routing_key AS routingKey,
            payload,
            status,
            attempt_count AS attemptCount,
            last_error AS lastError,
            created_at AS createdAt,
            published_at AS publishedAt,
            next_retry_at AS nextRetryAt
        FROM outbox_events
        WHERE status IN ('PENDING', 'FAILED')
          AND next_retry_at <= CURRENT_TIMESTAMP
        ORDER BY outbox_event_id
        LIMIT #{limit}
        FOR UPDATE SKIP LOCKED
        """)
    List<OutboxEvent> findBatchForPublish(@Param("limit") int limit);

    @Update("""
        UPDATE outbox_events
        SET status = 'PUBLISHED',
            published_at = CURRENT_TIMESTAMP,
            last_error = NULL
        WHERE outbox_event_id = #{outboxEventId}
        """)
    void markPublished(@Param("outboxEventId") Long outboxEventId);

    @Update("""
        UPDATE outbox_events
        SET status = 'FAILED',
            attempt_count = attempt_count + 1,
            last_error = #{lastError},
            next_retry_at = #{nextRetryAt}
        WHERE outbox_event_id = #{outboxEventId}
        """)
    void markFailed(
            @Param("outboxEventId") Long outboxEventId,
            @Param("lastError") String lastError,
            @Param("nextRetryAt") LocalDateTime nextRetryAt
    );
}