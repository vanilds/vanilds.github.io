package io.github.vanilds.creditdesk.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes committed outbox rows to RabbitMQ, oldest first.
 *
 * <ul>
 *   <li><b>{@code FOR UPDATE SKIP LOCKED}</b>: with two API replicas, each one takes a different batch instead of
 *   both publishing the same rows (or one waiting for the other).</li>
 *   <li><b>Publisher confirms + mandatory</b>: a row is marked as published only after the broker says it stored
 *   the message <i>and</i> routed it to a queue. Fire-and-forget would lose events silently on a broker restart.</li>
 *   <li><b>Stops at the first failure</b>: keeps the order and avoids hammering a broker that is down. The row stays
 *   pending and the next poll tries again; {@code creditdesk.outbox.pending} shows the backlog (alert in Phase 7).</li>
 * </ul>
 *
 * The cost: a crash between "broker confirmed" and "row marked" publishes that event again. That is why delivery
 * is <i>at least once</i> and every consumer deduplicates on the message id.
 */
@Component
@ConditionalOnProperty(name = "creditdesk.messaging.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 50;
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration retention;

    public OutboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbit, TransactionTemplate transaction, Clock clock,
                       MeterRegistry metrics, @Value("${creditdesk.messaging.outbox-retention}") Duration retention) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.transaction = transaction;
        this.clock = clock;
        this.retention = retention;
        Gauge.builder("creditdesk.outbox.pending", this::pending)
                .description("Events written but not yet published to RabbitMQ")
                .register(metrics);
    }

    private record PendingEvent(UUID id, String eventType, String payload, String correlationId, Timestamp createdAt) {
    }

    @Scheduled(fixedDelayString = "${creditdesk.messaging.outbox-poll}")
    public void relay() {
        int published;
        do {
            published = transaction.execute(status -> publishBatch());
        } while (published == BATCH); // a full batch means there may be more waiting
    }

    /** @return how many events were published (fewer than taken if one failed) */
    private int publishBatch() {
        List<PendingEvent> batch = jdbc.query("""
                SELECT id, event_type, payload::text AS payload, correlation_id, created_at
                FROM outbox_event
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED""",
                (rs, n) -> new PendingEvent(rs.getObject("id", UUID.class), rs.getString("event_type"),
                        rs.getString("payload"), rs.getString("correlation_id"), rs.getTimestamp("created_at")),
                BATCH);
        int published = 0;
        for (PendingEvent event : batch) {
            try {
                publish(event);
            } catch (Exception e) {
                String error = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("Outbox event {} ({}) not published, will retry: {}", event.id(), event.eventType(), error);
                jdbc.update("UPDATE outbox_event SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                        error.length() > 500 ? error.substring(0, 500) : error, event.id());
                break;
            }
            jdbc.update("UPDATE outbox_event SET published_at = ?, attempts = attempts + 1, last_error = NULL WHERE id = ?",
                    Timestamp.from(clock.instant()), event.id());
            published++;
        }
        return published;
    }

    private void publish(PendingEvent event) throws Exception {
        var properties = new MessageProperties();
        properties.setMessageId(event.id().toString());   // consumers deduplicate on this
        properties.setType(event.eventType());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT); // survives a broker restart
        properties.setTimestamp(Date.from(event.createdAt().toInstant()));
        properties.setAppId("creditdesk-api");
        if (event.correlationId() != null) {
            properties.setHeader(Topology.CORRELATION_HEADER, event.correlationId());
        }
        var message = new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties);

        var correlation = new CorrelationData(event.id().toString());
        rabbit.send(Topology.EVENTS, event.eventType(), message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("Unroutable: no queue bound for " + event.eventType());
        }
        if (!confirm.isAck()) {
            throw new IllegalStateException("Broker refused the message: " + confirm.getReason());
        }
    }

    /** Published rows are only needed for troubleshooting; RabbitMQ and the consumers have the data by now. */
    @Scheduled(cron = "0 15 3 * * *", zone = "America/Sao_Paulo")
    public void purgePublished() {
        int deleted = jdbc.update("DELETE FROM outbox_event WHERE published_at < ?",
                Timestamp.from(clock.instant().minus(retention.toMillis(), ChronoUnit.MILLIS)));
        if (deleted > 0) {
            log.info("Purged {} published outbox events", deleted);
        }
    }

    private double pending() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE published_at IS NULL", Long.class);
        return count == null ? 0 : count;
    }
}
