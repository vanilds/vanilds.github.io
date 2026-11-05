package io.github.vanilds.creditdesk.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * Makes a consumer idempotent. Delivery is at least once, so the same message can arrive twice; processing it
 * twice would mean two opinions, two audit lines, two status changes.
 *
 * <p>Called inside the consumer's transaction, before doing anything: the insert and the work commit together.
 * If the work fails, the insert rolls back too, so the retry is not mistaken for a duplicate. Two copies processed
 * at the same moment hit the same primary key: Postgres makes the second insert wait for the first transaction,
 * and once it commits the second one sees a conflict and skips.
 */
@Component
public class Inbox {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public Inbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return true the first time this consumer sees the message, false for a duplicate */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String consumer, UUID messageId) {
        return jdbc.update("""
                INSERT INTO processed_message (consumer, message_id, processed_at) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING""", consumer, messageId, Timestamp.from(clock.instant())) == 1;
    }
}
