package io.github.vanilds.creditdesk.proposal.opinion;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vanilds.creditdesk.messaging.FailedMessages;
import io.github.vanilds.creditdesk.messaging.Topology;
import io.github.vanilds.creditdesk.shared.web.CorrelationIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consumes {@code opinion.drafted}. Receives the raw {@link Message} instead of a converted object so it can tell
 * a <i>poison</i> message (bad JSON, failed validation: dead-letter now) from a <i>transient</i> failure
 * (retry later) — see {@link FailedMessages}.
 */
@Component
class OpinionListener {

    private final OpinionService service;
    private final FailedMessages failed;
    private final ObjectMapper json;
    private final Validator validator;

    OpinionListener(OpinionService service, FailedMessages failed, ObjectMapper json, Validator validator) {
        this.service = service;
        this.failed = failed;
        this.json = json;
        this.validator = validator;
    }

    @RabbitListener(queues = Topology.OPINIONS_QUEUE)
    void onOpinion(Message message) {
        Object correlationId = message.getMessageProperties().getHeader(Topology.CORRELATION_HEADER);
        if (correlationId != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId.toString()); // same id as the browser request
        }
        try {
            UUID opinionId;
            OpinionDrafted event;
            String messageId = message.getMessageProperties().getMessageId();
            if (messageId == null) {
                failed.deadLetter(message, Topology.OPINIONS_QUEUE, "unreadable message: no message_id to deduplicate on");
                return;
            }
            try {
                opinionId = UUID.fromString(messageId);
                event = json.readValue(message.getBody(), OpinionDrafted.class);
            } catch (IOException | RuntimeException unreadable) {
                failed.deadLetter(message, Topology.OPINIONS_QUEUE, "unreadable message: " + unreadable.getMessage());
                return;
            }
            Set<ConstraintViolation<OpinionDrafted>> violations = validator.validate(event);
            if (!violations.isEmpty()) {
                failed.deadLetter(message, Topology.OPINIONS_QUEUE, "invalid message: " + violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining(", ")));
                return;
            }
            try {
                service.record(opinionId, event);
            } catch (OpinionService.UnprocessableOpinion poison) {
                failed.deadLetter(message, Topology.OPINIONS_QUEUE, poison.getMessage());
            } catch (RuntimeException transientError) {
                failed.failed(message, Topology.OPINIONS_QUEUE, transientError);
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
