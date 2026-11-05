package io.github.vanilds.creditdesk.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * What a consumer does when handling a message fails:
 * <ul>
 *   <li><b>Poison message</b> (unreadable JSON, missing fields, unknown proposal): retrying cannot help, so it goes
 *   straight to the queue's {@code .dlq}.</li>
 *   <li><b>Transient failure</b> (database down, a concurrent update): reject it, the broker parks it in
 *   {@code .retry} for a while and then delivers it again — up to {@code max-attempts} times, then {@code .dlq}.</li>
 * </ul>
 * Never requeue immediately: a message that fails instantly would spin in a tight loop, burning CPU and filling
 * the logs, and would block the messages behind it.
 */
@Component
public class FailedMessages {

    private static final Logger log = LoggerFactory.getLogger(FailedMessages.class);

    private final RabbitTemplate rabbit;
    private final int maxAttempts;

    public FailedMessages(RabbitTemplate rabbit, @Value("${creditdesk.messaging.max-attempts}") int maxAttempts) {
        this.rabbit = rabbit;
        this.maxAttempts = maxAttempts;
    }

    /** The handler threw: decide between retry (by throwing) and dead letter. */
    public void failed(Message message, String queue, Exception error) {
        long attempt = previousFailures(message, queue) + 1;
        if (attempt >= maxAttempts) {
            deadLetter(message, queue, "gave up after %d attempts: %s".formatted(attempt, describe(error)));
            return;
        }
        log.warn("Message {} from {} failed (attempt {}/{}), retrying later: {}", message.getMessageProperties()
                .getMessageId(), queue, attempt, maxAttempts, describe(error));
        throw new AmqpRejectAndDontRequeueException("retry later", error);
    }

    /** Sends a copy to {@code queue.dlq} with the reason, so whoever reads the DLQ knows what went wrong. */
    public void deadLetter(Message message, String queue, String reason) {
        MessageProperties properties = message.getMessageProperties();
        properties.setHeader("x-dead-letter-reason", reason.length() > 500 ? reason.substring(0, 500) : reason);
        properties.setHeader("x-original-queue", queue);
        log.error("Message {} from {} sent to the dead-letter queue: {}", properties.getMessageId(), queue, reason);
        rabbit.send(Topology.DEAD_LETTER, queue, message);
    }

    /**
     * How many times this queue already rejected the message. The broker keeps the count in the {@code x-death}
     * header every time the message is dead-lettered (here: rejected into the retry exchange).
     */
    static long previousFailures(Message message, String queue) {
        Object header = message.getMessageProperties().getHeaders().get("x-death");
        if (!(header instanceof List<?> deaths)) {
            return 0;
        }
        for (Object death : deaths) {
            if (death instanceof Map<?, ?> entry && queue.equals(entry.get("queue"))
                    && "rejected".equals(String.valueOf(entry.get("reason")))
                    && entry.get("count") instanceof Number count) {
                return count.longValue();
            }
        }
        return 0;
    }

    private static String describe(Exception error) {
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }
}
