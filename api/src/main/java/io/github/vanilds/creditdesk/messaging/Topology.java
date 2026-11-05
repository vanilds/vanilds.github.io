package io.github.vanilds.creditdesk.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The RabbitMQ topology as code. Declarations are idempotent: RabbitMQ creates what is missing and checks that
 * what exists has the same arguments. The AI worker declares the same names (ai-worker/src/creditdesk_worker/topology.py).
 *
 * <pre>
 *                       proposal.submitted
 *  API (outbox relay) ──▶ creditdesk.events ──────────────────▶ ai-worker.proposal-submitted ──▶ worker
 *                         (topic)          opinion.drafted
 *  worker ───────────────▶ creditdesk.events ─────────────────▶ api.opinion-drafted ──▶ API
 *
 *  For every consumer queue Q:
 *    consumer fails ─▶ (reject) ─▶ creditdesk.retry ─▶ Q.retry (waits TTL) ─▶ back to Q only
 *    after N failures, or a poison message ─▶ creditdesk.dead-letter ─▶ Q.dlq (a human looks at it)
 * </pre>
 *
 * <p><b>Why the API also declares the worker's main queue:</b> a topic exchange drops messages that match no
 * queue. If the API published before the worker had ever started, the first submissions would be lost. The main
 * queue only has fixed arguments, so both sides can declare it. The retry delay is the consumer's own business,
 * so each side declares only its own retry queue.
 *
 * <p><b>Why retry goes back to Q and not to the exchange:</b> re-publishing to {@code creditdesk.events} would
 * deliver the message again to every other consumer of that routing key, which did nothing wrong.
 */
@Configuration(proxyBeanMethods = false)
public class Topology {

    public static final String EVENTS = "creditdesk.events";
    public static final String RETRY = "creditdesk.retry";
    public static final String DEAD_LETTER = "creditdesk.dead-letter";

    public static final String PROPOSAL_SUBMITTED = "proposal.submitted";
    public static final String OPINION_DRAFTED = "opinion.drafted";

    public static final String WORKER_QUEUE = "ai-worker.proposal-submitted";
    public static final String OPINIONS_QUEUE = "api.opinion-drafted";

    public static final String CORRELATION_HEADER = "x-correlation-id";

    /**
     * The retry delay is a queue argument (x-message-ttl), and RabbitMQ refuses to redeclare a queue with other
     * arguments. Without this, changing {@code creditdesk.messaging.retry-delay} would stop the API from starting
     * against an existing broker. With it, the mismatch is logged and the existing queue keeps its old delay; to
     * apply the new one, delete {@code api.opinion-drafted.retry} once it is empty (the worker does that by itself).
     */
    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        var admin = new RabbitAdmin(connectionFactory);
        admin.setIgnoreDeclarationExceptions(true);
        return admin;
    }

    @Bean
    Declarables rabbitTopology(@Value("${creditdesk.messaging.retry-delay}") Duration retryDelay) {
        var events = new TopicExchange(EVENTS, true, false);
        var retry = new DirectExchange(RETRY, true, false);
        var deadLetter = new DirectExchange(DEAD_LETTER, true, false);

        List<org.springframework.amqp.core.Declarable> all = new ArrayList<>(List.of(events, retry, deadLetter));

        // The worker's input: only the main queue and its binding (see the class comment)
        Queue workerQueue = mainQueue(WORKER_QUEUE);
        all.add(workerQueue);
        all.add(BindingBuilder.bind(workerQueue).to(events).with(PROPOSAL_SUBMITTED));

        // The API's own input, with its retry and dead-letter queues
        Queue opinions = mainQueue(OPINIONS_QUEUE);
        all.add(opinions);
        all.add(BindingBuilder.bind(opinions).to(events).with(OPINION_DRAFTED));
        all.addAll(retryAndDeadLetter(OPINIONS_QUEUE, retry, deadLetter, retryDelay));

        return new Declarables(all);
    }

    /** Durable; a rejected message goes to the retry exchange, keyed by the queue's own name. */
    static Queue mainQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(RETRY)
                .deadLetterRoutingKey(name)
                .build();
    }

    private static List<org.springframework.amqp.core.Declarable> retryAndDeadLetter(
            String name, DirectExchange retry, DirectExchange deadLetter, Duration delay) {
        // Messages wait here for the TTL, then expire into the default exchange ("") straight back to the queue
        Queue retryQueue = QueueBuilder.durable(name + ".retry")
                .ttl((int) delay.toMillis())
                .deadLetterExchange("")
                .deadLetterRoutingKey(name)
                .build();
        Queue dlq = QueueBuilder.durable(name + ".dlq").build();
        Binding toRetry = BindingBuilder.bind(retryQueue).to(retry).with(name);
        Binding toDlq = BindingBuilder.bind(dlq).to(deadLetter).with(name);
        return List.of(retryQueue, dlq, toRetry, toDlq);
    }
}
