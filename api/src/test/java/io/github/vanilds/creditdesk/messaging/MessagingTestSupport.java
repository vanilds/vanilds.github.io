package io.github.vanilds.creditdesk.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;

/** Reads a queue until the message a test is waiting for shows up (other tests publish to the same queues). */
public final class MessagingTestSupport {

    private MessagingTestSupport() {
    }

    public static Message receiveMatching(RabbitTemplate rabbit, String queue, Predicate<Message> wanted,
                                          Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Message message = rabbit.receive(queue, 500);
            if (message != null && wanted.test(message)) {
                return message;
            }
        }
        throw new AssertionError("No matching message in " + queue + " within " + timeout);
    }

    public static JsonNode body(ObjectMapper json, Message message) {
        try {
            return json.readTree(message.getBody());
        } catch (IOException e) {
            throw new AssertionError("Not JSON: " + new String(message.getBody()), e);
        }
    }
}
