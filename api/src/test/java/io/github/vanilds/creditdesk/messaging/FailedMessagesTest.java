package io.github.vanilds.creditdesk.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FailedMessagesTest {

    @Test
    void countsOnlyRejectionsFromTheGivenQueue() {
        var properties = new MessageProperties();
        properties.setHeader("x-death", List.of(
                Map.of("queue", "api.opinion-drafted.retry", "reason", "expired", "count", 2L),
                Map.of("queue", "api.opinion-drafted", "reason", "rejected", "count", 2L),
                Map.of("queue", "other", "reason", "rejected", "count", 7L)));
        var message = new Message(new byte[0], properties);

        assertThat(FailedMessages.previousFailures(message, "api.opinion-drafted")).isEqualTo(2);
        assertThat(FailedMessages.previousFailures(message, "nowhere")).isZero();
    }

    @Test
    void aFirstDeliveryHasNoFailures() {
        assertThat(FailedMessages.previousFailures(new Message(new byte[0], new MessageProperties()), "q")).isZero();
    }
}
