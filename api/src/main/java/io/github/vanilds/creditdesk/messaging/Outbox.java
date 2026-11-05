package io.github.vanilds.creditdesk.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vanilds.creditdesk.shared.web.CorrelationIdFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * Writes an event to the {@code outbox_event} table — not to RabbitMQ.
 *
 * <p><b>The problem it solves (dual write):</b> "save to Postgres, then publish to RabbitMQ" can fail in the
 * middle. Commit then crash before publishing → the proposal is SUBMITTED and nobody ever hears about it.
 * Publish then the commit fails → the worker drafts an opinion for a submission that never happened. There is no
 * transaction spanning a database and a broker (and 2PC/XA is not something you want in a bank's critical path).
 *
 * <p><b>The fix:</b> the event is just another row in the same database transaction as the change
 * ({@code MANDATORY}: calling it outside one is a bug). {@link OutboxRelay} publishes committed rows afterwards.
 * Result: an event exists if and only if the change committed, and it is delivered at least once.
 */
@Component
public class Outbox {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public Outbox(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** @return the event id, which becomes the AMQP {@code message_id} */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID add(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        UUID id = UUID.randomUUID();
        String body;
        try {
            body = json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Event payload is not serializable: " + eventType, e);
        }
        jdbc.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, correlation_id, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)""",
                id, aggregateType, aggregateId, eventType, body, CorrelationIdFilter.current(),
                Timestamp.from(clock.instant()));
        return id;
    }
}
